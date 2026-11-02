import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, tap } from 'rxjs';
import {
  MockOutcome,
  PaymentVerification,
  PlaceOrderRequest,
  PlacedOrder,
  Quote,
} from '../../../shared/models/storefront.models';
import { newUuid } from '../../../shared/util/validators';

@Injectable({ providedIn: 'root' })
export class CheckoutService {
  private readonly http = inject(HttpClient);
  private attemptKey: string | null = null;

  /** One key per checkout attempt; reused for retries/double-clicks until the attempt ends. */
  idempotencyKey(): string {
    if (!this.attemptKey) this.attemptKey = newUuid();
    return this.attemptKey;
  }

  /** Ends the attempt (order placed or customer deliberately starts over). */
  resetAttempt(): void {
    this.attemptKey = null;
  }

  preview(): Observable<Quote> {
    return this.http.post<Quote>('/api/v1/account/cart/checkout-preview', {});
  }

  placeOrder(body: PlaceOrderRequest): Observable<PlacedOrder> {
    const headers = new HttpHeaders({ 'Idempotency-Key': this.idempotencyKey() });
    return this.http
      .post<PlacedOrder>('/api/v1/checkout/orders', body, { headers })
      .pipe(tap(() => this.resetAttempt()));
  }

  verifyPayment(paymentId: number): Observable<PaymentVerification> {
    return this.http.post<PaymentVerification>(`/api/v1/payments/${paymentId}/verify`, {});
  }

  /** Local/test only: simulates the gateway side of the mock provider. */
  mockOutcome(paymentId: number, outcome: MockOutcome): Observable<void> {
    return this.http.post<void>(`/api/v1/dev/mock-payments/${paymentId}/outcome`, { outcome });
  }
}
