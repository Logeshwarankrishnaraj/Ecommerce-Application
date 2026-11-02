import { Component, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ProductCardComponent } from '../../../../shared/components/product-card/product-card.component';
import { Block, ProductCard } from '../../../../shared/models/storefront.models';
import { WishlistActions } from '../../../wishlist/services/wishlist-actions.service';
import { WishlistService } from '../../../wishlist/services/wishlist.service';

/** Renders one admin-configured content block. Only known block types are rendered; text is never injected as HTML. */
@Component({
  selector: 'app-block-view',
  imports: [RouterLink, ProductCardComponent],
  templateUrl: './block-view.component.html',
  styleUrl: './block-view.component.css',
})
export class BlockViewComponent {
  readonly block = input.required<Block>();
  protected readonly wishlist = inject(WishlistService);
  private readonly actions = inject(WishlistActions);

  toggleWish(p: ProductCard): void {
    this.actions.toggle(p.productId);
  }

  isExternal(url: string | null): boolean {
    return !!url && /^https?:\/\//i.test(url);
  }
}
