import { IconComponent } from '../../../shared/components/icon/icon.component';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { catchError, of, switchMap } from 'rxjs';
import { SiteConfigService } from '../../../core/site-config.service';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { LoadingStateComponent } from '../../../shared/components/loading-state/loading-state.component';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';
import { Address, MockOutcome, PaymentMethod, PlacedOrder, Quote } from '../../../shared/models/storefront.models';
import { errorMessage, toApiError } from '../../../shared/util/api-error.util';
import { AddressFormComponent } from '../../account/components/address-form.component';
import { AccountApiService } from '../../account/services/account-api.service';
import { CartService } from '../../cart/services/cart.service';
import { CheckoutService } from '../services/checkout.service';
import { InfoService } from '../../../core/info/info.service';

/**
 * Checkout. The server recomputes every total; expectedTotal only lets it detect a price change
 * so the customer can re-confirm. One Idempotency-Key per attempt prevents duplicate orders on double-click/retry.
 */
@Component({
  selector: 'app-checkout',
  imports: [FormsModule, RouterLink, IconComponent, MoneyPipe, AddressFormComponent, EmptyStateComponent, LoadingStateComponent],
  templateUrl: './checkout.component.html',
  styleUrl: './checkout.component.css',
})
export class CheckoutComponent {
  private readonly popup = inject(InfoService);
  private readonly checkout = inject(CheckoutService);
  private readonly account = inject(AccountApiService);
  private readonly cart = inject(CartService);
  private readonly site = inject(SiteConfigService);
  private readonly router = inject(Router);

  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly error = signal<string | null>(null);
  readonly quote = signal<Quote | null>(null);
  readonly addresses = signal<Address[]>([]);
  readonly addressId = signal<number | null>(null);
  readonly method = signal<PaymentMethod | null>(null);
  readonly terms = signal(false);
  readonly adding = signal(false);
  readonly busy = signal(false);
  readonly pendingOrder = signal<PlacedOrder | null>(null);
  readonly payMessage = signal<string | null>(null);
  readonly payFailed = signal(false);

  constructor() { this.init(); }

  methods(): PaymentMethod[] {
    return this.site.config().paymentMethods.filter((m): m is PaymentMethod => m === 'COD' || m === 'ONLINE');
  }

  canPlace(): boolean {
    const q = this.quote();
    return !!q && q.canCheckout && !!this.addressId() && !!this.method() && this.terms() && !this.busy();
  }

  init(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.cart.mergeGuestCart().pipe(catchError(() => of(null)), switchMap(() => this.checkout.preview())).subscribe({
      next: (q) => {
        this.quote.set(q);
        this.account.addresses().subscribe({
          next: (a) => {
            this.addresses.set(a);
            if (!this.addressId()) this.addressId.set((a.find((x) => x.isDefault) ?? a[0])?.addressId ?? null);
            if (!this.method()) this.method.set(this.methods()[0] ?? null);
            this.adding.set(a.length === 0);
            this.loading.set(false);
          },
          error: (e) => { this.loadError.set(errorMessage(e)); this.loading.set(false); },
        });
      },
      error: (e) => { this.loadError.set(errorMessage(e)); this.loading.set(false); },
    });
  }

  addressSaved(a: Address): void {
    this.adding.set(false);
    this.addressId.set(a.addressId ?? null);
    this.account.addresses().subscribe((l) => this.addresses.set(l));
  }

  place(): void {
    const q = this.quote();
    if (!q || !this.canPlace()) return;
    this.busy.set(true);
    this.error.set(null);
    this.checkout.placeOrder({ addressId: this.addressId()!, paymentMethod: this.method()!, expectedTotal: q.total, acceptedTerms: this.terms() }).subscribe({
      next: (o) => {
        this.busy.set(false);
        if (o.paymentMethod === 'COD') {
          this.cart.clear();
          void this.router.navigate(['/account/orders', o.orderId], { queryParams: { placed: 1 } });
        } else {
          this.pendingOrder.set(o);
        }
      },
      error: (e) => {
        this.busy.set(false);
        const code = toApiError(e).errorCode;
        this.popup.error(errorMessage(e));
        if (code === 'PRICE_CHANGED' || code === 'OUT_OF_STOCK') { this.checkout.resetAttempt(); this.init(); }
      },
    });
  }

  simulate(outcome: MockOutcome): void {
    const o = this.pendingOrder();
    if (!o) return;
    this.busy.set(true);
    this.payMessage.set(null);
    this.checkout.mockOutcome(o.payment.paymentId, outcome).subscribe({
      next: () => this.verify(),
      error: (e) => { this.busy.set(false); this.payFailed.set(true); this.popup.error(errorMessage(e)); },
    });
  }

  verify(): void {
    const o = this.pendingOrder();
    if (!o) return;
    this.busy.set(true);
    this.checkout.verifyPayment(o.payment.paymentId).subscribe({
      next: (v) => {
        this.busy.set(false);
        if (v.status === 'PAID') {
          this.cart.clear();
          void this.router.navigate(['/account/orders', v.orderId], { queryParams: { placed: 1 } });
        } else if (v.status === 'INITIATED') {
          this.payFailed.set(false);
          this.popup.warn('Payment is still pending.');
        } else {
          this.payFailed.set(true);
          this.popup.error('Payment was not completed and the order was cancelled. Your cart is unchanged — you can try again.');
          this.cart.load().subscribe({ error: () => undefined });
        }
      },
      error: (e) => { this.busy.set(false); this.payFailed.set(true); this.popup.error(errorMessage(e)); },
    });
  }
}
