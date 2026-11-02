import { Component, ElementRef, HostListener, effect, inject, signal, untracked } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { SiteConfigService } from '../../site-config.service';
import { CartService } from '../../../features/cart/services/cart.service';
import { ProductApiService } from '../../../features/catalog/services/product-api.service';
import { WishlistService } from '../../../features/wishlist/services/wishlist.service';
import { CategoryNode } from '../../../shared/models/storefront.models';
import { TooltipDirective } from '../../../shared/components/tooltip/tooltip.directive';
import { IconComponent } from '../../../shared/components/icon/icon.component';

import { ThemeToggleComponent } from '../../../shared/components/theme-toggle/theme-toggle.component';
@Component({
  selector: 'app-customer-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ReactiveFormsModule, TooltipDirective, IconComponent, ThemeToggleComponent],
  templateUrl: './customer-layout.component.html',
  styleUrl: './customer-layout.component.css',
})
export class CustomerLayoutComponent {
  protected readonly auth = inject(AuthService);
  protected readonly site = inject(SiteConfigService);
  protected readonly cart = inject(CartService);
  protected readonly wishlist = inject(WishlistService);
  private readonly products = inject(ProductApiService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  protected readonly year = new Date().getFullYear();
  readonly categories = signal<CategoryNode[]>([]);
  readonly menuOpen = signal(false);
  readonly accountOpen = signal(false);
  readonly searchForm = this.fb.nonNullable.group({ q: '' });

  constructor() {
    this.products.categories().subscribe({
      next: (c) => this.categories.set(c),
      error: () => this.categories.set([]),
    });
    // Wishlist is a customer-only endpoint (admins get 403). The cart works for everyone: customers use the server cart
    // (after merging anything saved before they signed in); visitors and staff sessions see the local cart.
    effect(() => {
      const customer = this.auth.isCustomer();
      untracked(() => {
        if (customer) {
          this.cart.mergeGuestCart().subscribe({ error: () => undefined });
          this.wishlist.load().subscribe({ error: () => undefined });
        } else {
          this.cart.showGuestCart();
          this.wishlist.clear();
        }
      });
    });
  }

  search(): void {
    const q = this.searchForm.controls.q.value.trim();
    this.menuOpen.set(false);
    this.router.navigate(['/products'], { queryParams: q ? { q } : {} });
  }

  logout(): void {
    this.accountOpen.set(false);
    this.auth.logout().subscribe({
      next: () => this.router.navigateByUrl('/'),
      error: () => {
        this.auth.setUser(null);
        this.router.navigateByUrl('/');
      },
    });
  }

  @HostListener('document:keydown.escape')
  onEscape(): void { this.closeMenus(); }

  /** Clicking anywhere outside the account menu closes it. */
  @HostListener('document:click', ['$event'])
  onDocClick(ev: Event): void {
    const acct = this.host.nativeElement.querySelector('.account');
    if (this.accountOpen() && acct && !acct.contains(ev.target as Node)) this.accountOpen.set(false);
  }

  closeMenus(): void {
    this.menuOpen.set(false);
    this.accountOpen.set(false);
  }

  isExternal(url: string): boolean {
    return /^https?:\/\//i.test(url);
  }
}
