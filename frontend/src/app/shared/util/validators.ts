import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/** SRS password policy: >= 10 chars with upper, lower, digit and symbol. */
export const passwordPolicy: ValidatorFn = (c: AbstractControl): ValidationErrors | null => {
  const v = String(c.value ?? '');
  if (!v) return null;
  const ok = v.length >= 10 && /[a-z]/.test(v) && /[A-Z]/.test(v) && /\d/.test(v) && /[^A-Za-z0-9]/.test(v);
  return ok ? null : { passwordPolicy: true };
};

export function matchesField(other: string): ValidatorFn {
  return (c: AbstractControl): ValidationErrors | null => {
    const sibling = c.parent?.get(other);
    return sibling && c.value !== sibling.value ? { mismatch: true } : null;
  };
}

export function safeReturnUrl(url: string | null | undefined): string {
  return url && url.startsWith('/') && !url.startsWith('//') ? url : '/';
}

export function newUuid(): string {
  const c = globalThis.crypto as Crypto | undefined;
  if (c && typeof c.randomUUID === 'function') return c.randomUUID();
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (ch) => {
    const r = (Math.random() * 16) | 0;
    return (ch === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });
}
