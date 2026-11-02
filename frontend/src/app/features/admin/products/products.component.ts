import { Component, OnInit, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AdminCategory, AdminProductRow } from '../models/admin.models';
import { AdminApiService } from '../shared/admin-api.service';
import { ConfirmService } from '../shared/confirm.service';
import { describeError, toApiError } from '../shared/form-errors';
import { NoticeService } from '../shared/notice.service';
import { PagerComponent } from '../shared/pager.component';
import { StateComponent } from '../shared/state.component';
import { TooltipDirective } from '../shared/tooltip.directive';

type Lifecycle = 'publish' | 'unpublish' | 'archive';

@Component({
  selector: 'admin-products',
  standalone: true,
  imports: [FormsModule, RouterLink, StateComponent, PagerComponent, TooltipDirective, DatePipe, CurrencyPipe],
  templateUrl: './products.component.html',
  styleUrl: './products.component.css',
})
export class ProductsComponent implements OnInit {
  private readonly api = inject(AdminApiService);
  private readonly confirm = inject(ConfirmService);
  private readonly notice = inject(NoticeService);

  protected readonly rows = signal<AdminProductRow[]>([]);
  protected readonly categories = signal<AdminCategory[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly total = signal(0);
  protected readonly busyId = signal<number | null>(null);

  protected q = '';
  protected status = '';
  protected categoryId = '';

  ngOnInit(): void {
    this.api.listCategories().subscribe({ next: (c) => this.categories.set(c), error: () => undefined });
    this.load();
  }

  protected search(): void { this.page.set(0); this.load(); }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.listProducts({ q: this.q, status: this.status, categoryId: this.categoryId, page: this.page(), size: 20 }).subscribe({
      next: (p) => {
        this.rows.set(p.content); this.totalPages.set(p.totalPages); this.total.set(p.totalElements); this.loading.set(false);
      },
      error: (e) => { this.error.set(describeError(toApiError(e))); this.loading.set(false); },
    });
  }

  protected goTo(p: number): void { this.page.set(p); this.load(); }

  protected async lifecycle(row: AdminProductRow, action: Lifecycle): Promise<void> {
    const wording: Record<Lifecycle, string> = {
      publish: 'It will become visible in the public catalogue.',
      unpublish: 'It will be hidden from the public catalogue and moved back to draft.',
      archive: 'It will be removed from the public catalogue. Existing order history is kept.',
    };
    const ok = await this.confirm.ask({
      title: `${action[0].toUpperCase()}${action.slice(1)} product?`,
      subject: `${row.sku} — ${row.title}`,
      message: wording[action],
      confirmLabel: action[0].toUpperCase() + action.slice(1),
      danger: action === 'archive',
    });
    if (!ok) return;
    this.busyId.set(row.productId);
    this.api.productLifecycle(row.productId, action).subscribe({
      next: () => { this.busyId.set(null); this.notice.success(`Product ${row.sku} updated.`); this.load(); },
      error: (e) => { this.busyId.set(null); this.notice.error(describeError(toApiError(e))); },
    });
  }
}
