import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { describe, expect, it, vi } from 'vitest';
import { AuthService } from '../auth/auth.service';
import { InfoService } from '../info/info.service';
import { AccountMe } from '../../shared/models/api.models';
import { apiErrorInterceptor } from './api-error.interceptor';
import { SESSION_EXPIRED_MESSAGE, isSessionExpiry, sessionExpiryTarget } from './session-expiry';

describe('sessionExpiryTarget', () => {
  it('sends customer-only pages to the customer sign-in with a way back', () => {
    expect(sessionExpiryTarget('/checkout')).toEqual({ commands: ['/login'], returnUrl: '/checkout' });
    expect(sessionExpiryTarget('/account/orders/5?placed=1')).toEqual({ commands: ['/login'], returnUrl: '/account/orders/5?placed=1' });
    expect(sessionExpiryTarget('/wishlist')?.commands).toEqual(['/login']);
  });

  it('sends admin pages to the admin sign-in', () => {
    expect(sessionExpiryTarget('/admin/orders?status=PLACED')).toEqual({ commands: ['/admin/login'], returnUrl: '/admin/orders?status=PLACED' });
    expect(sessionExpiryTarget('/admin')?.commands).toEqual(['/admin/login']);
  });

  it('leaves public pages and the sign-in screens alone', () => {
    for (const u of ['/', '/products?q=ring', '/products/gold-ring', '/cart', '/login', '/admin/login', '/accounting']) {
      expect(sessionExpiryTarget(u)).toBeNull();
    }
  });
});

describe('isSessionExpiry', () => {
  it('only counts a 401 on a non-auth API call for someone who was signed in', () => {
    expect(isSessionExpiry(401, '/api/v1/account/cart', true)).toBe(true);
    expect(isSessionExpiry(401, '/api/v1/account/cart', false)).toBe(false);
    expect(isSessionExpiry(401, '/api/v1/auth/password-reset/complete', true)).toBe(false);
    expect(isSessionExpiry(403, '/api/v1/account/cart', true)).toBe(false);
    expect(isSessionExpiry(401, '/other', true)).toBe(false);
  });
});

describe('apiErrorInterceptor on session expiry', () => {
  function setup(signedIn: boolean, url: string) {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(withInterceptors([apiErrorInterceptor])), provideHttpClientTesting()],
    });
    const auth = TestBed.inject(AuthService);
    auth.setUser(signedIn ? ({ userId: 1, fullName: 'T', username: 't', email: 't@x.y', mobileNumber: '9', emailVerified: true, roles: ['ROLE_CUSTOMER'] } as AccountMe) : null);
    const router = TestBed.inject(Router);
    vi.spyOn(router, 'url', 'get').mockReturnValue(url);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    return { auth, navigate, info: TestBed.inject(InfoService), http: TestBed.inject(HttpClient), ctl: TestBed.inject(HttpTestingController) };
  }

  it('warns, signs out and redirects from a page that needs a sign-in', () => {
    const { auth, navigate, info, http, ctl } = setup(true, '/account/orders');
    http.get('/api/v1/account/orders').subscribe({ error: () => undefined });
    ctl.expectOne('/api/v1/account/orders').flush({ errorCode: 'UNAUTHENTICATED', message: 'x', status: 401 }, { status: 401, statusText: 'Unauthorized' });
    expect(auth.isLoggedIn()).toBe(false);
    expect(info.messages().map((m) => m.text)).toEqual([SESSION_EXPIRED_MESSAGE]);
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { returnUrl: '/account/orders' } });
  });

  it('warns but stays on a public page', () => {
    const { navigate, info, http, ctl } = setup(true, '/products');
    http.get('/api/v1/account/wishlist').subscribe({ error: () => undefined });
    ctl.expectOne('/api/v1/account/wishlist').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(info.messages()).toHaveLength(1);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('says nothing for a visitor who was never signed in', () => {
    const { navigate, info, http, ctl } = setup(false, '/account/me');
    http.get('/api/v1/account/me').subscribe({ error: () => undefined });
    ctl.expectOne('/api/v1/account/me').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(info.messages()).toHaveLength(0);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('does not repeat the notice when several requests fail together', () => {
    const { info, http, ctl } = setup(true, '/checkout');
    http.get('/api/v1/account/cart').subscribe({ error: () => undefined });
    http.get('/api/v1/account/wishlist').subscribe({ error: () => undefined });
    ctl.match(() => true).forEach((r) => r.flush({}, { status: 401, statusText: 'Unauthorized' }));
    expect(info.messages()).toHaveLength(1);
  });

  it('does not treat a failed sign-in as an expired session', () => {
    const { info, http, ctl } = setup(true, '/login');
    http.post('/api/v1/auth/login', {}).subscribe({ error: () => undefined });
    ctl.expectOne('/api/v1/auth/login').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(info.messages()).toHaveLength(0);
  });
});
