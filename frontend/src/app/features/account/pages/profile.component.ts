import { AccountNavComponent } from '../../../shared/components/account-nav/account-nav.component';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../../core/auth/auth.service';
import { applyFieldErrors, controlMessage, errorMessage } from '../../../shared/util/api-error.util';
import { matchesField, passwordPolicy } from '../../../shared/util/validators';
import { AccountApiService } from '../services/account-api.service';
import { InfoService } from '../../../core/info/info.service';

@Component({
  selector: 'app-profile',
  imports: [AccountNavComponent, ReactiveFormsModule],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.css',
})
export class ProfileComponent {
  private readonly popup = inject(InfoService);
  protected readonly auth = inject(AuthService);
  private readonly api = inject(AccountApiService);
  private readonly fb = inject(FormBuilder);
  readonly pBusy = signal(false); readonly pMsg = signal<string | null>(null); readonly pOk = signal(false);
  readonly wBusy = signal(false); readonly wMsg = signal<string | null>(null); readonly wOk = signal(false);

  readonly profile = this.fb.nonNullable.group({
    fullName: [this.auth.user()?.fullName ?? '', [Validators.required, Validators.maxLength(120)]],
    mobileNumber: [this.auth.user()?.mobileNumber ?? '', [Validators.required, Validators.pattern(/^[0-9+() -]{7,20}$/)]],
  });
  readonly password = this.fb.nonNullable.group({
    currentPassword: ['', [Validators.required]],
    newPassword: ['', [Validators.required, passwordPolicy]],
    confirm: ['', [Validators.required, matchesField('newPassword')]],
  });

  m(form: { get(n: string): import('@angular/forms').AbstractControl | null }, name: string, label: string): string | null {
    return controlMessage(form.get(name), label);
  }

  saveProfile(): void {
    this.profile.markAllAsTouched();
    if (this.profile.invalid) { this.popup.error('Please complete the highlighted fields and try again.'); return; }
    if (this.pBusy()) return;
    this.pBusy.set(true); this.pMsg.set(null);
    this.api.updateProfile(this.profile.getRawValue()).subscribe({
      next: (u) => { this.auth.setUser(u); this.pBusy.set(false); this.popup.success('Profile updated.'); },
      error: (e) => { this.pBusy.set(false); this.popup.error(applyFieldErrors(this.profile, e).join(' ') || errorMessage(e)); },
    });
  }

  savePassword(): void {
    this.password.markAllAsTouched();
    if (this.password.invalid) { this.popup.error('Please complete the highlighted fields and try again.'); return; }
    if (this.wBusy()) return;
    this.wBusy.set(true); this.wMsg.set(null);
    const v = this.password.getRawValue();
    this.api.changePassword(v.currentPassword, v.newPassword).subscribe({
      next: () => { this.wBusy.set(false); this.popup.success('Password changed.'); this.password.reset(); },
      error: (e) => { this.wBusy.set(false); this.popup.error(applyFieldErrors(this.password, e).join(' ') || errorMessage(e)); },
    });
  }
}
