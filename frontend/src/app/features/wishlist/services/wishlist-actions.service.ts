import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { errorMessage } from '../../../shared/util/api-error.util';
import { WishlistService } from './wishlist.service';
import { InfoService } from '../../../core/info/info.service';

/** UI-level wishlist toggling: sends anonymous visitors to sign in first. */
@Injectable({ providedIn: 'root' })
export class WishlistActions {
  private readonly popup = inject(InfoService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly wishlist = inject(WishlistService);
  readonly error = signal<string | null>(null);

  toggle(productId: number): void {
    if (!this.auth.isCustomer()) {   // signed-out visitors and staff sessions both sign in as a customer
      this.router.navigate(['/login'], { queryParams: { returnUrl: this.router.url } });
      return;
    }
    this.error.set(null);
    this.wishlist.toggle(productId).subscribe({
      error: (e) => this.popup.error(errorMessage(e)),
    });
  }
}
