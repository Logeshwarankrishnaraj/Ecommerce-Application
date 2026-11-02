import { AccountNavComponent } from '../../../shared/components/account-nav/account-nav.component';
import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { LoadingStateComponent } from '../../../shared/components/loading-state/loading-state.component';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';
import { OrderSummary } from '../../../shared/models/storefront.models';
import { errorMessage } from '../../../shared/util/api-error.util';
import { statusClass, statusLabel } from '../order-status';
import { AccountApiService } from '../services/account-api.service';

@Component({
  selector: 'app-orders',
  imports: [AccountNavComponent, RouterLink, DatePipe, MoneyPipe, EmptyStateComponent, LoadingStateComponent],
  templateUrl: './orders.component.html',
  styleUrl: './orders.component.css',
})
export class OrdersComponent {
  private readonly api = inject(AccountApiService);
  readonly rows = signal<OrderSummary[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  protected readonly label = statusLabel;
  protected readonly cls = statusClass;

  constructor() { this.load(0); }

  load(p: number): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.orders(p, 10).subscribe({
      next: (r) => { this.rows.set(r.content); this.page.set(r.page); this.totalPages.set(r.totalPages); this.loading.set(false); },
      error: (e) => { this.error.set(errorMessage(e)); this.loading.set(false); },
    });
  }
}
