import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { LoadingStateComponent } from '../../../shared/components/loading-state/loading-state.component';
import { ProductCardComponent } from '../../../shared/components/product-card/product-card.component';
import { errorMessage } from '../../../shared/util/api-error.util';
import { WishlistService } from '../services/wishlist.service';
import { CartService } from '../../cart/services/cart.service';
import { toGuestLine } from '../../cart/services/guest-cart';
import { WishlistItem } from '../../../shared/models/storefront.models';
import { InfoService } from '../../../core/info/info.service';

@Component({
  selector: 'app-wishlist',
  imports: [ProductCardComponent, EmptyStateComponent, LoadingStateComponent, RouterLink],
  templateUrl: './wishlist.component.html',
  styleUrl: './wishlist.component.css',
})
export class WishlistComponent {
  private readonly popup = inject(InfoService);
  protected readonly wishlist = inject(WishlistService);
  private readonly cart = inject(CartService);
  /** Product being moved to the cart right now. */
  protected readonly movingId = signal<number | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  constructor() {
    this.wishlist.load().subscribe({ next: () => this.loading.set(false), error: (e) => { this.error.set(errorMessage(e)); this.loading.set(false); } });
  }

  remove(productId: number): void {
    this.wishlist.remove(productId).subscribe({ error: (e) => this.popup.error(errorMessage(e)) });
  }

  /** Adds one unit to the cart, then takes the item off the wishlist. If the add fails the item stays saved. */
  moveToCart(item: WishlistItem): void {
    if (this.movingId() !== null) return;
    this.movingId.set(item.productId);
    this.cart.add(item.productId, 1, toGuestLine(item.product, 1)).subscribe({
      next: () => {
        this.wishlist.remove(item.productId).subscribe({
          next: () => { this.movingId.set(null); this.popup.success(`${item.product.title} was moved to your cart.`); },
          error: () => { this.movingId.set(null); this.popup.warn(`${item.product.title} is in your cart, but it could not be removed from your wishlist.`); },
        });
      },
      error: (e) => { this.movingId.set(null); this.popup.error(errorMessage(e)); },
    });
  }
}
