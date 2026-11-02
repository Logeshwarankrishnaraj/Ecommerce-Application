import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { statusClass, statusLabel } from '../../account/order-status';
import { AdminOrderRow, ORDER_STATUSES } from '../models/admin.models';
import { AdminApiService } from '../shared/admin-api.service';
import { describeError, toApiError } from '../shared/form-errors';
import { PagerComponent } from '../shared/pager.component';
import { exportFileName } from './order-export';
import { StateComponent } from '../shared/state.component';

@Component({
  selector: 'admin-orders',
  standalone: true,
  imports: [FormsModule, RouterLink, DatePipe, StateComponent, PagerComponent],
  templateUrl: './orders.component.html',
  styleUrl: './orders.component.css',
})
export class OrdersAdminComponent implements OnInit {
  private readonly api = inject(AdminApiService);
  protected readonly statuses = ORDER_STATUSES;
  protected readonly label = statusLabel;
  protected readonly cls = statusClass;
  protected readonly rows = signal<AdminOrderRow[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly total = signal(0);
  protected readonly exporting = signal(false);
  protected readonly exportError = signal<string | null>(null);
  protected q = '';
  protected status = '';
  /** Filters last applied to the table; the export uses these so the file matches what is on screen. */
  private appliedQ = '';
  private appliedStatus = '';

  ngOnInit(): void { this.load(); }
  protected search(): void { this.page.set(0); this.load(); }
  protected goTo(p: number): void { this.page.set(p); this.load(); }
  protected load(): void {
    this.loading.set(true); this.error.set(null);
    if (this.page() === 0) { this.appliedQ = this.q; this.appliedStatus = this.status; }
    this.api.listOrders({ q: this.q, status: this.status, page: this.page(), size: 20 }).subscribe({
      next: (p) => { this.rows.set(p.content); this.totalPages.set(p.totalPages); this.total.set(p.totalElements); this.loading.set(false); },
      error: (e) => { this.error.set(describeError(toApiError(e))); this.loading.set(false); },
    });
  }

  protected exportCsv(): void {
    if (this.exporting()) return;
    this.exporting.set(true); this.exportError.set(null);
    this.api.exportOrders({ q: this.appliedQ, status: this.appliedStatus }).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url; a.download = exportFileName(this.appliedStatus); a.click();
        URL.revokeObjectURL(url);
        this.exporting.set(false);
      },
      error: () => { this.exportError.set('The export could not be created. Please try again.'); this.exporting.set(false); },
    });
  }
}
