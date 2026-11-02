/** Message shown when the server ends a signed-in session (timeout, or the account was disabled). */
export const SESSION_EXPIRED_MESSAGE = 'Your session has expired. Please sign in again.';

export interface SessionExpiryTarget {
  commands: string[];
  returnUrl: string;
}

/**
 * Where to send someone whose session just ended. Pages that need a sign-in go to the matching sign-in screen and come
 * back afterwards; public pages (catalogue, product, cart) stay put, since they work signed out.
 */
export function sessionExpiryTarget(currentUrl: string): SessionExpiryTarget | null {
  const path = currentUrl.split(/[?#]/)[0];
  if (path === '/admin/login') return null;
  if (path === '/admin' || path.startsWith('/admin/')) return { commands: ['/admin/login'], returnUrl: currentUrl };
  if (/^\/(checkout|account|wishlist)(\/|$)/.test(path)) return { commands: ['/login'], returnUrl: currentUrl };
  return null;
}

/** Only calls outside /auth/ can mean "your session ended"; sign-in, OTP and reset failures use 401 for other reasons. */
export function isSessionExpiry(status: number, url: string, wasSignedIn: boolean): boolean {
  return status === 401 && wasSignedIn && url.startsWith('/api/') && !url.startsWith('/api/v1/auth/');
}
