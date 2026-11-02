import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LookupGroup, LookupValue } from '../models/admin.models';
import { AdminApiService } from '../shared/admin-api.service';
import { ConfirmService } from '../shared/confirm.service';
import { describeError, toApiError } from '../shared/form-errors';
import { NoticeService } from '../shared/notice.service';
import { StateComponent } from '../shared/state.component';

@Component({
  selector: 'admin-lookups',
  standalone: true,
  imports: [FormsModule, StateComponent],
  templateUrl: './lookups.component.html',
  styleUrl: './lookups.component.css',
})
export class LookupsComponent implements OnInit {
  private readonly api = inject(AdminApiService);
  private readonly confirm = inject(ConfirmService);
  private readonly notice = inject(NoticeService);
  protected readonly groups = signal<LookupGroup[]>([]);
  protected readonly values = signal<LookupValue[]>([]);
  protected readonly group = signal('');
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected nv = { valueCode: '', displayLabel: '', sortOrder: 0 };

  ngOnInit(): void {
    this.api.listLookupGroups().subscribe({
      next: (g) => { this.groups.set(g); if (g.length) this.pick(g[0].groupCode); else this.loading.set(false); },
      error: (e) => { this.error.set(describeError(toApiError(e))); this.loading.set(false); },
    });
  }
  protected pick(code: string): void { this.group.set(code); this.loadValues(); }
  protected loadValues(): void {
    this.loading.set(true); this.error.set(null);
    this.api.listLookupValues(this.group()).subscribe({ next: (v) => { this.values.set(v); this.loading.set(false); }, error: (e) => { this.error.set(describeError(toApiError(e))); this.loading.set(false); } });
  }
  protected add(): void {
    if (!this.nv.valueCode.trim() || !this.nv.displayLabel.trim()) { this.notice.error('Code and label are required.'); return; }
    this.busy.set(true);
    this.api.createLookupValue(this.group(), { ...this.nv, valueCode: this.nv.valueCode.trim().toUpperCase().replace(/\s+/g, '_') }).subscribe({
      next: () => { this.busy.set(false); this.nv = { valueCode: '', displayLabel: '', sortOrder: 0 }; this.notice.success('Value added.'); this.loadValues(); },
      error: (e) => { this.busy.set(false); this.notice.error(describeError(toApiError(e))); },
    });
  }
  protected rename(v: LookupValue, ev: Event): void {
    const label = (ev.target as HTMLInputElement).value.trim();
    if (!label || label === v.displayLabel) return;
    this.api.updateLookupValue(v.lovId, { displayLabel: label }).subscribe({ next: () => { this.notice.success('Label updated.'); this.loadValues(); }, error: (e) => { this.notice.error(describeError(toApiError(e))); this.loadValues(); } });
  }
  protected toggle(v: LookupValue): void {
    this.api.updateLookupValue(v.lovId, { active: !v.active }).subscribe({ next: () => this.loadValues(), error: (e) => this.notice.error(describeError(toApiError(e))) });
  }
  protected async remove(v: LookupValue): Promise<void> {
    const ok = await this.confirm.ask({ title: 'Delete value?', subject: v.displayLabel, message: 'Values already used by products cannot be deleted; deactivate them instead.', confirmLabel: 'Delete', danger: true });
    if (!ok) return;
    this.api.deleteLookupValue(v.lovId).subscribe({ next: () => { this.notice.success('Value deleted.'); this.loadValues(); }, error: (e) => this.notice.error(describeError(toApiError(e))) });
  }
}
