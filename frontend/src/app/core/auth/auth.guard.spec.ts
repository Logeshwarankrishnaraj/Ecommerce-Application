import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { AccountMe } from '../../shared/models/api.models';
import { customerGuard } from './auth.guard';
import { AuthService } from './auth.service';

function user(roles: string[]): AccountMe {
  return { userId: 1, fullName: 'T', username: 't', email: 't@x.y', mobileNumber: '9', emailVerified: true, roles } as AccountMe;
}

function run(roles: string[] | null, url: string): boolean | UrlTree {
  TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
  TestBed.inject(AuthService).setUser(roles ? user(roles) : null);
  return TestBed.runInInjectionContext(() => customerGuard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot)) as boolean | UrlTree;
}

describe('customerGuard', () => {
  it('lets a customer through', () => {
    expect(run(['ROLE_CUSTOMER'], '/cart')).toBe(true);
  });

  it('sends a signed-out visitor to the customer sign-in', () => {
    const r = run(null, '/wishlist') as UrlTree;
    expect(TestBed.inject(Router).serializeUrl(r)).toBe('/login?returnUrl=%2Fwishlist');
  });

  it('never redirects a staff session to the admin console', () => {
    for (const roles of [['ROLE_ADMIN'], ['ROLE_ADMIN', 'ROLE_SUPER_ADMIN']]) {
      TestBed.resetTestingModule();
      const r = run(roles, '/cart') as UrlTree;
      const url = TestBed.inject(Router).serializeUrl(r);
      expect(url).toBe('/login?returnUrl=%2Fcart');
      expect(url).not.toContain('admin');
    }
  });
});
