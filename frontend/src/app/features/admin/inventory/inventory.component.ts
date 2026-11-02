import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { InventoryMovement, InventoryRow } from '../models/admin.models';
import { AdminApiService } from '../shared/admin-api.service';
import { describeError, toApiError } from '../shared/form-errors';
import { NoticeService } from '../shared/notice.service';
import { PagerComponent } from '../shared/pager.component';
import { StateComponent } from '../shared/state.component';

@Component({
  selector: 'admin-inventory',
  standalone: true,
  imports: [FormsModule, DatePipe, StateComponent, PagerComponent],
  templateUrl: './inventory.component.html',
  styleUrl: './inventory.component.css',
})
export class InventoryComponent implements OnInit {
  private readonly api = inject(AdminApiService);
  private readonly notice = inject(NoticeService);
  protected readonly rows = signal<InventoryRow[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly total = signal(0);
  protected readonly adjusting = signal<InventoryRow | null>(null);
  protected readonly history = signal<{ row: InventoryRow; rows: InventoryMovement[] } | null>(null);
  protected readonly busy = signal(false);
  protected q = '';
  protected lowOnly = false;
  protected delta: number | null = null;
  protected reason = '';

  ngOnInit(): void { this.load(); }
  protected search(): void { this.page.set(0); this.load(); }
  protected goTo(p: number): void { this.page.set(p); this.load(); }
  protected load(): void {
    this.loading.set(true); this.error.set(null);
    this.api.listInventory({ q: this.q, lowOnly: this.lowOnly || null, page: this.page(), size: 20 }).subscribe({
      next: (p) => { this.rows.set(p.content); this.totalPages.set(p.totalPages); this.total.set(p.totalElements); this.loading.set(false); },
      error: (e) => { this.error.set(describeError(toApiError(e))); this.loading.set(false); },
    });
  }
  protected startAdjust(r: InventoryRow): void { this.adjusting.set(r); this.delta = null; this.reason = ''; }
  protected saveAdjust(): void {
    const r = this.adjusting();
    if (!r || this.busy()) return;
    if (!this.delta || !Number.isInteger(this.delta)) { this.notice.error('Enter a non-zero whole number.'); return; }
    if (this.reason.trim().length < 3) { this.notice.error('Please give a reason.'); return; }
    this.busy.set(true);
    this.api.adjustInventory({ productId: r.productId, quantityDelta: this.delta, reason: this.reason.trim() }).subscribe({
      next: () => { this.busy.set(false); this.adjusting.set(null); this.notice.success('Stock adjusted.'); this.load(); },
      error: (e) => { this.busy.set(false); this.notice.error(describeError(toApiError(e))); },
    });
  }
  protected showHistory(r: InventoryRow): void {
    this.api.inventoryMovements(r.productId, { page: 0, size: 50 }).subscribe({ next: (p) => this.history.set({ row: r, rows: p.content }), error: (e) => this.notice.error(describeError(toApiError(e))) });
  }
}
