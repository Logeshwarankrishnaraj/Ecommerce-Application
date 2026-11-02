import { IconComponent } from '../../../shared/components/icon/icon.component';
import { Component, effect, inject, input, signal } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { SiteConfigService } from '../../../core/site-config.service';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { ImageGalleryComponent } from '../../../shared/components/image-gallery/image-gallery.component';
import { LoadingStateComponent } from '../../../shared/components/loading-state/loading-state.component';
import { ProductCardComponent } from '../../../shared/components/product-card/product-card.component';
import { MoneyPipe } from '../../../shared/pipes/money.pipe';
import { ProductCard, ProductDetail } from '../../../shared/models/storefront.models';
import { errorMessage, toApiError } from '../../../shared/util/api-error.util';
import { CartService } from '../../cart/services/cart.service';
import { toGuestLine } from '../../cart/services/guest-cart';
import { WishlistActions } from '../../wishlist/services/wishlist-actions.service';
import { WishlistService } from '../../wishlist/services/wishlist.service';
import { ProductApiService } from '../services/product-api.service';
import { InfoService } from '../../../core/info/info.service';

@Component({
  selector: 'app-product-detail',
  imports: [ImageGalleryComponent, LoadingStateComponent, EmptyStateComponent, ProductCardComponent, MoneyPipe, IconComponent],
  templateUrl: './product-detail.component.html',
  styleUrl: './product-detail.component.css',
})
export class ProductDetailComponent {
  private readonly popup = inject(InfoService);
  readonly slug = input.required<string>();
  private readonly api = inject(ProductApiService);
  private readonly title = inject(Title);
  private readonly cart = inject(CartService);
  private readonly site = inject(SiteConfigService);
  protected readonly wishlist = inject(WishlistService);
  protected readonly actions = inject(WishlistActions);

  readonly product = signal<ProductDetail | null>(null);
  readonly related = signal<ProductCard[]>([]);
  readonly loading = signal(true);
  readonly notFound = signal(false);
  readonly error = signal<string | null>(null);
  readonly qty = signal(1);
  readonly busy = signal(false);
  readonly message = signal<string | null>(null);
  readonly isError = signal(false);

  constructor() {
    effect(() => this.load(this.slug()));
  }

  load(slug: string): void {
    this.loading.set(true);
    this.notFound.set(false);
    this.error.set(null);
    this.message.set(null);
    this.api.detail(slug).subscribe({
      next: (p) => {
        this.product.set(p);
        this.qty.set(1);
        this.loading.set(false);
        this.title.setTitle(p.seoTitle || `${p.title} | ${this.site.config().shop.displayName}`);
        this.api.recordView(p.productId).subscribe({ error: () => undefined });   // analytics must never break browsing
        this.api.related(p.productId).subscribe({ next: (r) => this.related.set(r), error: () => this.related.set([]) });
      },
      error: (e) => {
        if (toApiError(e).status === 404) this.notFound.set(true);
        else this.error.set(errorMessage(e));
        this.loading.set(false);
      },
    });
  }

  maxQty(p: ProductDetail): number {
    return Math.max(1, Math.min(p.availableQuantity || 1, this.site.config().cartMaxQty));
  }

  step(d: number, p: ProductDetail): void { this.qty.set(Math.min(Math.max(this.qty() + d, 1), this.maxQty(p))); }

  setQty(ev: Event, p: ProductDetail): void {
    const n = Math.floor(Number((ev.target as HTMLInputElement).value));
    this.qty.set(Number.isFinite(n) ? Math.min(Math.max(n, 1), this.maxQty(p)) : 1);
  }

  addToCart(p: ProductDetail): void {
    this.busy.set(true);
    this.message.set(null);
    // Visitors get a local cart (merged into their account at sign-in); customers use the server cart.
    this.cart.add(p.productId, this.qty(), toGuestLine(p, this.qty())).subscribe({
      next: () => { this.busy.set(false); this.popup.success('Added to your cart.'); },
      error: (e) => { this.busy.set(false); this.popup.error(errorMessage(e)); },
    });
  }
}
