import { Component, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Meta } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { controlMessage } from '../../../shared/util/api-error.util';
import { safeReturnUrl } from '../../../shared/util/validators';
import { InfoService } from '../../../core/info/info.service';

/** Staff-only sign-in. Standalone page (no storefront header or links); customers who find it cannot use it. */
@Component({
  selector: 'app-admin-login',
  imports: [ReactiveFormsModule],
  templateUrl: './admin-login.component.html',
  styleUrl: './admin-login.component.css',
})
export class AdminLoginComponent {
  private readonly popup = inject(InfoService);
  readonly returnUrl = input<string | undefined>();
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly form = this.fb.nonNullable.group({
    usernameOrEmail: ['', [Validators.required, Validators.maxLength(254)]],
    password: ['', [Validators.required, Validators.maxLength(128)]],
  });

  constructor() {
    inject(Meta).updateTag({ name: 'robots', content: 'noindex,nofollow' });
    if (this.auth.isAdmin()) void this.router.navigateByUrl('/admin');
  }

  msg(name: 'usernameOrEmail' | 'password'): string | null {
    return controlMessage(this.form.controls[name], name === 'password' ? 'Password' : 'Username or email');
  }

  private fail(): void {
    this.busy.set(false);
    this.form.controls.password.reset('');
    this.popup.error('Invalid username/email or password.');
  }

  submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) { this.popup.error('Please complete the highlighted fields and try again.'); return; }
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set(null);
    const v = this.form.getRawValue();
    this.auth.login(v.usernameOrEmail.trim(), v.password).subscribe({
      next: (u) => {
        if (!u.roles.some((r) => r === 'ROLE_ADMIN' || r === 'ROLE_SUPER_ADMIN')) {
          // A valid customer login is not a valid staff login: end that session and answer generically.
          this.auth.logout().subscribe({ complete: () => this.fail(), error: () => this.fail() });
          return;
        }
        this.busy.set(false);
        const r = this.returnUrl();
        void this.router.navigateByUrl(r && safeReturnUrl(r).startsWith('/admin') ? safeReturnUrl(r) : '/admin');
      },
      error: (e: { message?: string; status?: number }) => {
        this.busy.set(false);
        this.form.controls.password.reset('');
        this.popup.error(e?.status === 429 ? (e.message ?? 'Too many attempts. Please wait and try again.') : 'Invalid username/email or password.');
      },
    });
  }
}
