import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AdminCategory, CategoryUpsert } from '../models/admin.models';
import { AdminApiService } from '../shared/admin-api.service';
import { ConfirmService } from '../shared/confirm.service';
import { describeError, toApiError } from '../shared/form-errors';
import { NoticeService } from '../shared/notice.service';
import { StateComponent } from '../shared/state.component';
import { InfoService } from '../../../core/info/info.service';

@Component({
  selector: 'admin-categories',
  standalone: true,
  imports: [FormsModule, StateComponent],
  templateUrl: './categories.component.html',
  styleUrl: './categories.component.css',
})
export class CategoriesComponent implements OnInit {
  private readonly popup = inject(InfoService);
  private readonly api = inject(AdminApiService);
  private readonly confirm = inject(ConfirmService);
  private readonly notice = inject(NoticeService);
  protected readonly rows = signal<AdminCategory[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly editing = signal(false);
  protected readonly editId = signal<number | null>(null);
  protected readonly busy = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected f: CategoryUpsert = this.blank();

  private blank(): CategoryUpsert { return { parentCategoryId: null, name: '', slug: '', description: '', active: true, sortOrder: 0 }; }
  ngOnInit(): void { this.load(); }
  protected parents(): AdminCategory[] { return this.rows().filter((c) => c.categoryId !== this.editId()); }
  protected parentName(c: AdminCategory): string { return this.rows().find((x) => x.categoryId === c.parentCategoryId)?.name ?? '—'; }

  protected load(): void {
    this.loading.set(true); this.error.set(null);
    this.api.listCategories().subscribe({ next: (r) => { this.rows.set(r); this.loading.set(false); }, error: (e) => { this.error.set(describeError(toApiError(e))); this.loading.set(false); } });
  }
  protected startNew(): void { this.f = this.blank(); this.editId.set(null); this.formError.set(null); this.editing.set(true); }
  protected edit(c: AdminCategory): void {
    this.f = { parentCategoryId: c.parentCategoryId, name: c.name, slug: c.slug, description: c.description ?? '', active: c.active, sortOrder: c.sortOrder };
    this.editId.set(c.categoryId); this.formError.set(null); this.editing.set(true);
  }
  protected save(): void {
    if (!this.f.name.trim() || this.busy()) { this.popup.error('Name is required.'); return; }
    this.busy.set(true); this.formError.set(null);
    const body = { ...this.f, slug: this.f.slug?.trim() || null };
    const id = this.editId();
    (id ? this.api.updateCategory(id, body) : this.api.createCategory(body)).subscribe({
      next: () => { this.busy.set(false); this.editing.set(false); this.notice.success('Category saved.'); this.load(); },
      error: (e) => { this.busy.set(false); this.popup.error(describeError(toApiError(e))); },
    });
  }
  protected async remove(c: AdminCategory): Promise<void> {
    const ok = await this.confirm.ask({ title: 'Delete category?', subject: c.name, message: 'This only works for categories with no products or sub-categories.', confirmLabel: 'Delete', danger: true });
    if (!ok) return;
    this.api.deleteCategory(c.categoryId).subscribe({ next: () => { this.notice.success('Category deleted.'); this.load(); }, error: (e) => this.notice.error(describeError(toApiError(e))) });
  }
}
