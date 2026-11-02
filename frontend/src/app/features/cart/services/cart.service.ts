import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, concat, defer, finalize, forkJoin, map, of, shareReplay, switchMap, tap, throwError, toArray } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { InfoService } from '../../../core/info/info.service';
import { SiteConfigService } from '../../../core/site-config.service';
import { ApiError } from '../../../shared/models/api.models';
import { Cart, ProductDetail } from '../../../shared/models/storefront.models';
import { toApiError } from '../../../shared/util/api-error.util';
import { ProductApiService } from '../../catalog/services/product-api.service';
import {
  GuestLine,
  GuestResult,
  addToGuestLines,
  applyFresh,
  guestCartStorage,
  guestToCart,
  planMerge,
  removeGuestLine,
  setGuestQuantity,
} from './guest-cart';

const EMPTY: Cart = { items: [], subtotal: 0, itemCount: 0, hasIssues: false, currency: 'INR' };

type MergeOutcome = { productId: number; result: 'added' | 'rejected' | 'retry' };

/**
 * The cart. Signed-in customers use the server cart (the server stays authoritative for prices and stock). Anyone else
 * (signed out, or a staff session) gets a local cart that is merged into the customer's cart at sign-in.
 */
@Injectable({ providedIn: 'root' })
export class CartService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly products = inject(ProductApiService);
  private readonly site = inject(SiteConfigService);
  private readonly info = inject(InfoService);
  private readonly base = '/api/v1/account/cart';
  private readonly state = signal<Cart>(EMPTY);

  private guestLines: GuestLine[] = guestCartStorage.read();
  private mergeInFlight: Observable<Cart> | null = null;

  readonly cart = this.state.asReadonly();
  readonly count = computed(() => this.state().itemCount);
  readonly loading = signal(false);

  constructor() {
    if (this.isGuest()) this.state.set(guestToCart(this.guestLines));
  }

  private isGuest(): boolean {
    return !this.auth.isCustomer();
  }

  private maxQty(): number {
    return this.site.config().cartMaxQty;
  }

  /** Header badge for visitors: what is in the local cart, with no network call. */
  showGuestCart(): void {
    this.state.set(guestToCart(this.guestLines));
  }

  load(): Observable<Cart> {
    if (this.isGuest()) return this.refreshGuest();
    this.loading.set(true);
    return this.http.get<Cart>(this.base).pipe(
      tap({
        next: (c) => {
          this.state.set(c);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      }),
    );
  }

  /** `snapshot` is used only for the local cart; the server looks the product up itself. */
  add(productId: number, quantity = 1, snapshot?: GuestLine): Observable<Cart> {
    if (this.isGuest()) {
      return this.guestMutate(() =>
        snapshot && snapshot.productId === productId
          ? addToGuestLines(this.guestLines, { ...snapshot, quantity }, this.maxQty())
          : { lines: this.guestLines, error: 'This item could not be added. Please try again.' },
      );
    }
    return this.http.post<Cart>(`${this.base}/items`, { productId, quantity }).pipe(tap((c) => this.state.set(c)));
  }

  /** For visitors `cartItemId` is the product id (see guestToCart). */
  updateQuantity(cartItemId: number, quantity: number): Observable<Cart> {
    if (this.isGuest()) return this.guestMutate(() => setGuestQuantity(this.guestLines, cartItemId, quantity, this.maxQty()));
    return this.http.patch<Cart>(`${this.base}/items/${cartItemId}`, { quantity }).pipe(tap((c) => this.state.set(c)));
  }

  remove(cartItemId: number): Observable<Cart> {
    if (this.isGuest()) return this.guestMutate(() => ({ lines: removeGuestLine(this.guestLines, cartItemId), error: null }));
    return this.http.delete<Cart>(`${this.base}/items/${cartItemId}`).pipe(tap((c) => this.state.set(c)));
  }

  clear(): void {
    this.state.set(EMPTY);
  }

  /**
   * Moves the local cart into the signed-in customer's cart, then loads the result. Safe to call from several places:
   * overlapping calls share one merge, and with nothing saved locally it simply loads the account cart.
   */
  mergeGuestCart(): Observable<Cart> {
    if (this.mergeInFlight) return this.mergeInFlight;
    if (this.guestLines.length === 0) return this.load();

    // defer: nothing is taken out of the local cart until something actually subscribes.
    const merge$ = defer(() => {
      const pending = this.guestLines;
      this.guestLines = [];
      guestCartStorage.write([]);
      const serverCart$ = this.http.get<Cart>(this.base).pipe(
        catchError((e) => {
          this.restore(pending);   // could not even read the account cart: keep everything for next time
          return throwError(() => e);
        }),
      );
      return serverCart$.pipe(
        switchMap((server) => {
          const plan = planMerge(pending, server.items.map((i) => ({ productId: i.productId, quantity: i.quantity })), this.maxQty());
          if (plan.length === 0) return of<MergeOutcome[]>([]);
          return concat(
            ...plan.map((p) =>
              this.http.post<Cart>(`${this.base}/items`, p).pipe(
                map((): MergeOutcome => ({ productId: p.productId, result: 'added' })),
                catchError((e) => of<MergeOutcome>({ productId: p.productId, result: isTransient(toApiError(e).status) ? 'retry' : 'rejected' })),
              ),
            ),
          ).pipe(toArray());
        }),
        tap((outcomes) => this.finishMerge(pending, outcomes)),
        switchMap(() => this.load()),
      );
    }).pipe(
      finalize(() => {
        this.mergeInFlight = null;
      }),
      shareReplay({ bufferSize: 1, refCount: false }),
    );
    this.mergeInFlight = merge$;
    return merge$;
  }

  // ---- local cart internals

  private guestMutate(op: () => GuestResult): Observable<Cart> {
    const r = op();
    if (r.error) {
      const err: ApiError = { status: 422, errorCode: 'CART_RULE', message: r.error };
      return throwError(() => err);
    }
    this.guestLines = r.lines;
    guestCartStorage.write(r.lines);
    const cart = guestToCart(r.lines);
    this.state.set(cart);
    return of(cart);
  }

  /** Re-checks each saved item against the public product data so the visitor sees current prices and stock. */
  private refreshGuest(): Observable<Cart> {
    const lines = this.guestLines;
    if (lines.length === 0) {
      this.state.set(EMPTY);
      return of(EMPTY);
    }
    this.loading.set(true);
    return forkJoin(
      lines.map((l) =>
        this.products.detail(l.slug).pipe(
          map((fresh): { id: number; fresh: ProductDetail | null | undefined } => ({ id: l.productId, fresh })),
          // 404 = product gone; any other failure keeps the saved snapshot instead of hiding the cart
          catchError((e) => of({ id: l.productId, fresh: toApiError(e).status === 404 ? null : undefined })),
        ),
      ),
    ).pipe(
      map((results) => {
        // Apply to the lines as they are now (the visitor may have changed the cart while this was loading).
        this.guestLines = this.guestLines.map((l) => {
          const r = results.find((x) => x.id === l.productId);
          return r && r.fresh !== undefined ? applyFresh(l, r.fresh) : l;
        });
        guestCartStorage.write(this.guestLines);
        const cart = guestToCart(this.guestLines);
        this.state.set(cart);
        return cart;
      }),
      finalize(() => this.loading.set(false)),
    );
  }

  private restore(lines: GuestLine[]): void {
    this.guestLines = [...this.guestLines, ...lines.filter((l) => !this.guestLines.some((g) => g.productId === l.productId))];
    guestCartStorage.write(this.guestLines);
  }

  private finishMerge(pending: GuestLine[], outcomes: MergeOutcome[]): void {
    const retry = outcomes.filter((o) => o.result === 'retry').map((o) => pending.find((p) => p.productId === o.productId));
    this.restore(retry.filter((l): l is GuestLine => !!l));   // network or server trouble: try again at the next sign-in
    const rejected = outcomes.filter((o) => o.result === 'rejected').length;
    const added = outcomes.filter((o) => o.result === 'added').length;
    if (rejected > 0) {
      this.info.warn(`${rejected === 1 ? 'One item' : rejected + ' items'} from before you signed in could not be added because ${rejected === 1 ? 'it is' : 'they are'} unavailable.`);
    } else if (added > 0) {
      this.info.success('The items you saved before signing in are now in your cart.');
    }
  }
}

function isTransient(status: number): boolean {
  return status === 0 || status >= 500;
}
