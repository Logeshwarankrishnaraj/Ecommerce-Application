import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { Wishlist } from '../../../shared/models/storefront.models';

@Injectable({ providedIn: 'root' })
export class WishlistService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/account/wishlist';
  private readonly state = signal<Wishlist>({ items: [] });

  readonly wishlist = this.state.asReadonly();
  readonly count = computed(() => this.state().items.length);
  readonly ids = computed(() => new Set(this.state().items.map((i) => i.productId)));

  load(): Observable<Wishlist> {
    return this.http.get<Wishlist>(this.base).pipe(tap((w) => this.state.set(w)));
  }

  add(productId: number): Observable<Wishlist> {
    return this.http.post<Wishlist>(`${this.base}/items`, { productId }).pipe(tap((w) => this.state.set(w)));
  }

  remove(productId: number): Observable<Wishlist> {
    return this.http.delete<Wishlist>(`${this.base}/items/${productId}`).pipe(tap((w) => this.state.set(w)));
  }

  has(productId: number): boolean {
    return this.ids().has(productId);
  }

  toggle(productId: number): Observable<Wishlist> {
    return this.has(productId) ? this.remove(productId) : this.add(productId);
  }

  clear(): void {
    this.state.set({ items: [] });
  }
}
