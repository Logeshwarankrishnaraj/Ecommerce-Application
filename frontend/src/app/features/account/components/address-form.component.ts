import { Component, effect, inject, input, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Address } from '../../../shared/models/storefront.models';
import { applyFieldErrors, controlMessage, errorMessage } from '../../../shared/util/api-error.util';
import { AccountApiService } from '../services/account-api.service';
import { InfoService } from '../../../core/info/info.service';

@Component({
  selector: 'app-address-form',
  imports: [ReactiveFormsModule],
  templateUrl: './address-form.component.html',
  styleUrl: './address-form.component.css',
})
export class AddressFormComponent {
  private readonly popup = inject(InfoService);
  readonly address = input<Address | null>(null);
  readonly saved = output<Address>();
  readonly cancelled = output<void>();
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(AccountApiService);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);

  readonly fields = [
    { name: 'recipientName' as const, label: 'Recipient name', auto: 'name' },
    { name: 'phone' as const, label: 'Phone', auto: 'tel' },
    { name: 'line1' as const, label: 'Address line 1', auto: 'address-line1' },
    { name: 'line2' as const, label: 'Address line 2 (optional)', auto: 'address-line2' },
    { name: 'locality' as const, label: 'Area / locality (optional)', auto: 'address-level3' },
    { name: 'city' as const, label: 'City', auto: 'address-level2' },
    { name: 'state' as const, label: 'State', auto: 'address-level1' },
    { name: 'postalCode' as const, label: 'PIN / postal code', auto: 'postal-code' },
    { name: 'country' as const, label: 'Country', auto: 'country-name' },
  ];

  readonly form = this.fb.nonNullable.group({
    recipientName: ['', [Validators.required, Validators.maxLength(150)]],
    phone: ['', [Validators.required, Validators.pattern(/^[0-9+() -]{7,20}$/)]],
    line1: ['', [Validators.required, Validators.maxLength(200)]],
    line2: ['', [Validators.maxLength(200)]],
    locality: ['', [Validators.maxLength(120)]],
    city: ['', [Validators.required, Validators.maxLength(100)]],
    state: ['', [Validators.required, Validators.maxLength(100)]],
    postalCode: ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9 -]{3,12}$/)]],
    country: ['India', [Validators.required, Validators.maxLength(60)]],
    isDefault: [false],
  });

  constructor() {
    effect(() => {
      const a = this.address();
      if (a) this.form.patchValue({ ...a, line2: a.line2 ?? '', locality: a.locality ?? '' });
    });
  }

  msg(name: (typeof this.fields)[number]['name']): string | null {
    return controlMessage(this.form.controls[name], this.fields.find((f) => f.name === name)?.label ?? name);
  }

  save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) { this.popup.error('Please complete the highlighted fields and try again.'); return; }
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set(null);
    const v = this.form.getRawValue();
    const body: Address = { ...v, line2: v.line2.trim() || null, locality: v.locality.trim() || null };
    const id = this.address()?.addressId;
    (id ? this.api.updateAddress(id, body) : this.api.createAddress(body)).subscribe({
      next: (a) => { this.busy.set(false); this.saved.emit(a); },
      error: (e) => { this.busy.set(false); this.popup.error(applyFieldErrors(this.form, e).join(' ') || errorMessage(e)); },
    });
  }
}
