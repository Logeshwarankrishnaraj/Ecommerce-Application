import { AccountNavComponent } from '../../../shared/components/account-nav/account-nav.component';
import { DatePipe } from '@angular/common';
import { Component, effect, inject, input, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { LoadingStateComponent } from '../../../shared/components/loading-state/loading-state.component';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';
import { OrderDetail } from '../../../shared/models/storefront.models';
import { errorMessage, toApiError } from '../../../shared/util/api-error.util';
import { statusClass, statusLabel } from '../order-status';
import { AccountApiService } from '../services/account-api.service';
import { InfoService } from '../../../core/info/info.service';

@Component({
  selector: 'app-order-detail',
  imports: [AccountNavComponent, RouterLink, DatePipe, MoneyPipe, EmptyStateComponent, LoadingStateComponent],
  templateUrl: './order-detail.component.html',
  styleUrl: './order-detail.component.css',
})
export class OrderDetailComponent {
  private readonly popup = inject(InfoService);
  readonly orderId = input.required<string>();
  private readonly api = inject(AccountApiService);
  protected readonly placed = inject(ActivatedRoute).snapshot.queryParamMap.get('placed') === '1';
  readonly order = signal<OrderDetail | null>(null);
  readonly loading = signal(true);
  readonly notFound = signal(false);
  readonly error = signal<string | null>(null);
  readonly busy = signal(false);
  protected readonly label = statusLabel;
  protected readonly cls = statusClass;

  constructor() { effect(() => this.load(this.orderId())); }

  load(id: string): void {
    this.loading.set(true);
    this.notFound.set(false);
    this.error.set(null);
    this.api.order(Number(id)).subscribe({
      next: (o) => { this.order.set(o); this.loading.set(false); },
      error: (e) => { if (toApiError(e).status === 404) this.notFound.set(true); else this.error.set(errorMessage(e)); this.loading.set(false); },
    });
  }

  cancel(o: OrderDetail): void {
    if (!confirm(`Cancel order ${o.orderNumber}? This cannot be undone.`)) return;
    this.busy.set(true);
    this.error.set(null);
    this.api.cancelOrder(o.orderId).subscribe({
      next: (u) => { this.order.set(u); this.busy.set(false); },
      error: (e) => { this.popup.error(errorMessage(e)); this.busy.set(false); },
    });
  }
}
