import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { IconComponent } from '../icon/icon.component';

/** Tab bar shared by the signed-in customer pages. */
@Component({
  selector: 'app-account-nav',
  imports: [RouterLink, RouterLinkActive, IconComponent],
  template: `
    <nav class="acct" aria-label="Account">
      <p class="who muted small">Signed in as <strong>{{ auth.user()?.fullName }}</strong></p>
      <div class="tabs-row">
        <a routerLink="/account/profile" routerLinkActive="on"><app-icon name="user" [size]="18" /> Profile</a>
        <a routerLink="/account/addresses" routerLinkActive="on"><app-icon name="store" [size]="18" /> Addresses</a>
        <a routerLink="/account/orders" routerLinkActive="on"><app-icon name="receipt" [size]="18" /> Orders</a>
        <a routerLink="/wishlist" routerLinkActive="on"><app-icon name="heart" [size]="18" /> Wishlist</a>
      </div>
    </nav>`,
  styles: [`
    .acct { margin-bottom: var(--sp-5); }
    .who { margin: 0 0 var(--sp-2); }
    .tabs-row { display: flex; gap: var(--sp-1); overflow-x: auto; border-bottom: 1px solid var(--c-border); scrollbar-width: none; }
    .tabs-row::-webkit-scrollbar { display: none; }
    a { display: inline-flex; align-items: center; gap: 8px; min-height: 44px; padding: 0 var(--sp-4); color: var(--c-muted); text-decoration: none; font-weight: 500; white-space: nowrap; border-bottom: 2px solid transparent; margin-bottom: -1px; }
    a:hover { color: var(--c-text); }
    a.on { color: var(--c-accent); border-bottom-color: var(--c-accent); }
  `],
})
export class AccountNavComponent {
  protected readonly auth = inject(AuthService);
}
