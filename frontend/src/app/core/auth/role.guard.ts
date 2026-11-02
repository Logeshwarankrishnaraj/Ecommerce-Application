import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/** UX aid only; the backend is the real authorization boundary. */
export const adminGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.isAdmin()) return true;
  // Signed-out visitors go to the separate admin sign-in; a signed-in customer is simply sent back to the store.
  return auth.isLoggedIn() ? router.parseUrl('/') : router.createUrlTree(['/admin/login'], { queryParams: { returnUrl: state.url } });
};
