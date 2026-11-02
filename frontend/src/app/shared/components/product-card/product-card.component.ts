import { Component, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ProductCard } from '../../models/storefront.models';
import { MoneyPipe } from '../../pipes/money.pipe';
import { TooltipDirective } from '../tooltip/tooltip.directive';

@Component({
  selector: 'app-product-card',
  imports: [RouterLink, MoneyPipe, TooltipDirective],
  templateUrl: './product-card.component.html',
  styleUrl: './product-card.component.css',
})
export class ProductCardComponent {
  readonly product = input.required<ProductCard>();
  readonly wished = input(false);
  readonly showWishlist = input(true);
  readonly wishlistToggle = output<ProductCard>();
  /** Shows an "Add to cart" button on in-stock cards (list pages). */
  readonly showAddToCart = input(false);
  readonly adding = input(false);
  readonly addToCart = output<ProductCard>();

  get discountPercent(): number {
    const p = this.product();
    return p.salePrice !== null && p.price > 0 ? Math.round((1 - p.salePrice / p.price) * 100) : 0;
  }

  get onSale(): boolean {
    const p = this.product();
    return p.salePrice !== null && p.salePrice < p.price;
  }
}
