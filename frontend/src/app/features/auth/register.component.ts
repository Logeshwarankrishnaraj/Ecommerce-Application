import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService, OtpChallenge } from '../../core/auth/auth.service';
import { applyFieldErrors, controlMessage, errorMessage } from '../../shared/util/api-error.util';
import { matchesField, passwordPolicy } from '../../shared/util/validators';
import { InfoService } from '../../core/info/info.service';
import { catchError, of } from 'rxjs';
import { CartService } from '../cart/services/cart.service';

/** Two steps: details -> email OTP + password. The account exists only after OTP verification. */
@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './register.component.html',
  styleUrl: './register.component.css',
})
export class RegisterComponent {
  private readonly popup = inject(InfoService);
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly cart = inject(CartService);
  readonly step = signal<1 | 2>(1);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly info = signal<string | null>(null);
  private challenge: OtpChallenge | null = null;

  readonly fields = [
    { name: 'fullName' as const, label: 'Full name', type: 'text', auto: 'name' },
    { name: 'username' as const, label: 'Username', type: 'text', auto: 'username' },
    { name: 'email' as const, label: 'Email', type: 'email', auto: 'email' },
    { name: 'mobileNumber' as const, label: 'Mobile number', type: 'tel', auto: 'tel' },
  ];
  readonly details = this.fb.nonNullable.group({
    fullName: ['', [Validators.required, Validators.maxLength(120)]],
    username: ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9_.-]{3,30}$/)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    mobileNumber: ['', [Validators.required, Validators.pattern(/^[0-9+() -]{7,20}$/)]],
  });
  readonly verify = this.fb.nonNullable.group({
    otp: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
    password: ['', [Validators.required, passwordPolicy]],
    confirm: ['', [Validators.required, matchesField('password')]],
  });

  minutes(): number {
    return Math.round((this.challenge?.expiresInSeconds ?? 600) / 60);
  }
  dMsg(n: keyof typeof this.details.controls): string | null {
    return controlMessage(this.details.controls[n], this.fields.find((f) => f.name === n)?.label ?? n);
  }
  vMsg(n: keyof typeof this.verify.controls): string | null {
    return controlMessage(this.verify.controls[n], n === 'otp' ? 'Code' : 'Password');
  }

  requestOtp(): void {
    this.details.markAllAsTouched();
    if (this.details.invalid) { this.popup.error('Please complete the highlighted fields and try again.'); return; }
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set(null);
    const v = this.details.getRawValue();
    this.auth.requestRegistrationOtp({ ...v, fullName: v.fullName.trim(), username: v.username.trim(), email: v.email.trim() }).subscribe({
      next: (c) => { this.challenge = c; this.busy.set(false); this.step.set(2); },
      error: (e) => { this.busy.set(false); this.popup.error(applyFieldErrors(this.details, e).join(' ') || errorMessage(e)); },
    });
  }

  resend(): void {
    if (!this.challenge) return;
    this.busy.set(true);
    this.error.set(null);
    this.auth.resendRegistrationOtp(this.challenge.challengeId).subscribe({
      next: (c) => { this.challenge = c; this.busy.set(false); this.popup.success('A new code was sent.'); },
      error: (e) => { this.busy.set(false); this.popup.error(errorMessage(e)); },
    });
  }

  back(): void {
    this.step.set(1);
    this.info.set(null);
    this.error.set(null);
  }

  complete(): void {
    this.verify.markAllAsTouched();
    if (this.verify.invalid) { this.popup.error('Please complete the highlighted fields and try again.'); return; }
    if (this.busy() || !this.challenge) return;
    this.busy.set(true);
    this.error.set(null);
    this.info.set(null);
    const v = this.verify.getRawValue();
    this.auth.completeRegistration(this.challenge.challengeId, v.otp, v.password).subscribe({
      next: () => {
        this.cart.mergeGuestCart().pipe(catchError(() => of(null))).subscribe(() => {
          this.busy.set(false);
          void this.router.navigateByUrl('/');
        });
      },
      error: (e) => { this.busy.set(false); this.popup.error(applyFieldErrors(this.verify, e).join(' ') || errorMessage(e)); },
    });
  }
}
