import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { applyFieldErrors, controlMessage, errorMessage } from '../../shared/util/api-error.util';
import { passwordPolicy } from '../../shared/util/validators';
import { InfoService } from '../../core/info/info.service';

@Component({
  selector: 'app-forgot-password',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './forgot-password.component.html',
  styleUrl: './forgot-password.component.css',
})
export class ForgotPasswordComponent {
  private readonly popup = inject(InfoService);
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  readonly step = signal<1 | 2>(1);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly info = signal<string | null>(null);
  readonly request = this.fb.nonNullable.group({ email: ['', [Validators.required, Validators.email]] });
  readonly complete = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
    newPassword: ['', [Validators.required, passwordPolicy]],
  });

  m(form: { get(n: string): import('@angular/forms').AbstractControl | null }, name: string): string | null {
    return controlMessage(form.get(name), name === 'newPassword' ? 'Password' : name === 'code' ? 'Code' : 'Email');
  }

  send(): void {
    this.request.markAllAsTouched();
    if (this.request.invalid) { this.popup.error('Please complete the highlighted fields and try again.'); return; }
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set(null);
    this.auth.requestPasswordReset(this.request.controls.email.value.trim()).subscribe({
      next: (r) => {
        this.busy.set(false);
        this.popup.success(r.message);
        this.complete.controls.email.setValue(this.request.controls.email.value.trim());
        this.step.set(2);
      },
      error: (e) => { this.busy.set(false); this.popup.error(errorMessage(e)); },
    });
  }

  finish(): void {
    this.complete.markAllAsTouched();
    if (this.complete.invalid) { this.popup.error('Please complete the highlighted fields and try again.'); return; }
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set(null);
    const v = this.complete.getRawValue();
    this.auth.completePasswordReset(v.email.trim(), v.code, v.newPassword).subscribe({
      next: () => { this.busy.set(false); void this.router.navigateByUrl('/login'); },
      error: (e) => { this.busy.set(false); this.popup.error(applyFieldErrors(this.complete, e).join(' ') || errorMessage(e)); },
    });
  }
}
