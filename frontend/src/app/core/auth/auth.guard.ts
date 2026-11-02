import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/** UX aid only; the backend enforces authorization. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.isLoggedIn() ? true : router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/**
 * Customer-only pages (cart, wishlist, checkout, account). Anyone who is not a customer - a signed-out visitor, or a
 * staff account whose session is still open in this browser - is asked to sign in as a customer. The storefront never
 * redirects to the admin console; staff reach it only through /admin. UX aid only; the backend enforces authorization.
 */
export const customerGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.isCustomer() ? true : router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};
