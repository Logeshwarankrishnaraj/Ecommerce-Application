import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../core/auth/auth.service';
import { IconComponent } from '../../../shared/components/icon/icon.component';
import { AuditRow } from '../models/admin.models';
import { AdminApiService } from '../shared/admin-api.service';
import { ConfirmService } from '../shared/confirm.service';
import { describeError, toApiError } from '../shared/form-errors';
import { NoticeService } from '../shared/notice.service';
import { PagerComponent } from '../shared/pager.component';
import { StateComponent } from '../shared/state.component';

@Component({
  selector: 'admin-audit',
  standalone: true,
  imports: [FormsModule, DatePipe, StateComponent, PagerComponent, IconComponent],
  templateUrl: './audit.component.html',
  styleUrl: './audit.component.css',
})
export class AuditComponent implements OnInit {
  private readonly api = inject(AdminApiService);
  private readonly confirm = inject(ConfirmService);
  private readonly notice = inject(NoticeService);
  protected readonly auth = inject(AuthService);
  protected readonly rows = signal<AuditRow[]>([]);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly total = signal(0);
  protected readonly selected = signal<ReadonlySet<number>>(new Set());
  protected readonly allOnPageSelected = computed(() => this.rows().length > 0 && this.rows().every((r) => this.selected().has(r.auditId)));
  protected action = '';
  protected actor = '';

  ngOnInit(): void { this.load(); }
  protected search(): void { this.page.set(0); this.load(); }
  protected goTo(p: number): void { this.page.set(p); this.load(); }
  protected load(): void {
    this.loading.set(true); this.error.set(null);
    this.api.listAudit({ action: this.action.trim(), actor: this.actor.trim(), page: this.page(), size: 25 }).subscribe({
      next: (p) => {
        this.rows.set(p.content); this.totalPages.set(p.totalPages); this.total.set(p.totalElements);
        this.selected.set(new Set());
        // deleting the last rows of the last page leaves it empty: step back one page
        if (p.content.length === 0 && this.page() > 0) { this.page.set(this.page() - 1); this.load(); return; }
        this.loading.set(false);
      },
      error: (e) => { this.error.set(describeError(toApiError(e))); this.loading.set(false); },
    });
  }

  protected toggle(id: number): void {
    const next = new Set(this.selected());
    if (next.has(id)) next.delete(id); else next.add(id);
    this.selected.set(next);
  }
  protected toggleAll(): void {
    this.selected.set(this.allOnPageSelected() ? new Set() : new Set(this.rows().map((r) => r.auditId)));
  }

  protected async deleteOne(r: AuditRow): Promise<void> {
    const ok = await this.confirm.ask({ title: 'Delete audit entry?', subject: `${r.action} · ${r.actor}`, message: 'This permanently removes the entry. The deletion itself will be recorded.', confirmLabel: 'Delete', danger: true });
    if (ok) this.run({ ids: [r.auditId] });
  }
  protected async deleteSelected(): Promise<void> {
    const ids = [...this.selected()];
    if (ids.length === 0) return;
    const ok = await this.confirm.ask({ title: `Delete ${ids.length} audit ${ids.length === 1 ? 'entry' : 'entries'}?`, message: 'This permanently removes the selected entries. The deletion itself will be recorded.', confirmLabel: 'Delete selected', danger: true });
    if (ok) this.run({ ids });
  }
  protected async deleteAll(): Promise<void> {
    const ok = await this.confirm.ask({ title: 'Delete the entire audit log?', subject: `${this.total()} entries`, message: 'Every audit entry is permanently removed, including entries that do not match the current filters. Only a record of this deletion remains.', confirmLabel: 'Delete everything', danger: true });
    if (ok) this.run({ all: true });
  }

  private run(body: { ids?: number[]; all?: boolean }): void {
    this.busy.set(true);
    this.api.deleteAudit(body).subscribe({
      next: (r) => { this.busy.set(false); this.notice.success(`${r.deleted} audit ${r.deleted === 1 ? 'entry' : 'entries'} deleted.`); this.load(); },
      error: (e) => { this.busy.set(false); this.notice.error(describeError(toApiError(e))); },
    });
  }
}
