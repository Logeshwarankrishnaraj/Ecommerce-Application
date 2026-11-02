import { Component, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { applyFieldErrors, controlMessage, errorMessage } from '../../shared/util/api-error.util';
import { safeReturnUrl } from '../../shared/util/validators';
import { InfoService } from '../../core/info/info.service';
import { catchError, of } from 'rxjs';
import { CartService } from '../cart/services/cart.service';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css',
})
export class LoginComponent {
  private readonly popup = inject(InfoService);
  readonly returnUrl = input<string | undefined>();
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly cart = inject(CartService);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly form = this.fb.nonNullable.group({
    usernameOrEmail: ['', [Validators.required, Validators.maxLength(254)]],
    password: ['', [Validators.required, Validators.maxLength(128)]],
  });

  msg(name: 'usernameOrEmail' | 'password'): string | null {
    return controlMessage(this.form.controls[name], name === 'password' ? 'Password' : 'Username or email');
  }

  private fail(message: string): void {
    this.busy.set(false);
    this.form.controls.password.reset('');
    this.popup.error(message);
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
        if (u.roles.some((r) => r === 'ROLE_ADMIN' || r === 'ROLE_SUPER_ADMIN')) {
          // Staff accounts use the separate admin portal; the storefront never reveals that this was a valid staff login.
          this.auth.logout().subscribe({ complete: () => this.fail('Invalid username/email or password.'), error: () => this.fail('Invalid username/email or password.') });
          return;
        }
        // Bring anything added before signing in into the account cart first, so the next page (e.g. checkout) sees it.
        this.cart.mergeGuestCart().pipe(catchError(() => of(null))).subscribe(() => {
          this.busy.set(false);
          void this.router.navigateByUrl(safeReturnUrl(this.returnUrl()));
        });
      },
      error: (e) => {
        this.busy.set(false);
        this.form.controls.password.reset('');
        this.popup.error(applyFieldErrors(this.form, e).join(' ') || errorMessage(e));
      },
    });
  }
}
