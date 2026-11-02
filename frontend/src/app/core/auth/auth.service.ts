import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, of, tap } from 'rxjs';
import { AccountMe } from '../../shared/models/api.models';

export interface OtpChallenge {
  challengeId: string | number;
  expiresInSeconds: number;
  resendAfterSeconds: number;
}

export interface RegistrationDetails {
  fullName: string;
  username: string;
  email: string;
  mobileNumber: string;
}

/** Session-cookie based auth state (no tokens in localStorage). */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly userSignal = signal<AccountMe | null>(null);

  readonly user = this.userSignal.asReadonly();
  readonly isLoggedIn = computed(() => this.userSignal() !== null);
  readonly isAdmin = computed(() => {
    const roles = this.userSignal()?.roles ?? [];
    return roles.includes('ROLE_ADMIN') || roles.includes('ROLE_SUPER_ADMIN');
  });
  readonly isCustomer = computed(() => (this.userSignal()?.roles ?? []).includes('ROLE_CUSTOMER'));
  readonly isSuperAdmin = computed(() => (this.userSignal()?.roles ?? []).includes('ROLE_SUPER_ADMIN'));

  /** Primes the CSRF cookie. */
  init(): Observable<unknown> {
    return this.http.get('/api/v1/auth/csrf').pipe(catchError(() => of(null)));
  }

  restoreSession(): Observable<AccountMe | null> {
    return this.http.get<AccountMe>('/api/v1/account/me').pipe(
      tap((u) => this.userSignal.set(u)),
      catchError(() => {
        this.userSignal.set(null);
        return of(null);
      }),
    );
  }

  setUser(user: AccountMe | null): void {
    this.userSignal.set(user);
  }

  login(usernameOrEmail: string, password: string): Observable<AccountMe> {
    return this.http
      .post<AccountMe>('/api/v1/auth/login', { usernameOrEmail, password })
      .pipe(tap((u) => this.userSignal.set(u)));
  }

  logout(): Observable<void> {
    return this.http.post<void>('/api/v1/auth/logout', {}).pipe(tap(() => this.userSignal.set(null)));
  }

  requestRegistrationOtp(details: RegistrationDetails): Observable<OtpChallenge> {
    return this.http.post<OtpChallenge>('/api/v1/auth/registration/request-otp', details);
  }

  resendRegistrationOtp(challengeId: string | number): Observable<OtpChallenge> {
    return this.http.post<OtpChallenge>('/api/v1/auth/registration/resend-otp', { challengeId });
  }

  completeRegistration(challengeId: string | number, otp: string, password: string): Observable<AccountMe> {
    return this.http
      .post<AccountMe>('/api/v1/auth/registration/verify-and-complete', { challengeId, otp, password })
      .pipe(tap((u) => this.userSignal.set(u)));
  }

  requestPasswordReset(email: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>('/api/v1/auth/password-reset/request', { email });
  }

  completePasswordReset(email: string, code: string, newPassword: string): Observable<void> {
    return this.http.post<void>('/api/v1/auth/password-reset/complete', { email, code, newPassword });
  }
}
