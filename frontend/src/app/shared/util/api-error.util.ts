import { HttpErrorResponse } from '@angular/common/http';
import { AbstractControl, FormGroup } from '@angular/forms';
import { ApiError } from '../models/api.models';

/** Normalises any HTTP/transport failure to the contract's ApiError shape. */
export function toApiError(err: unknown): ApiError {
  if (err instanceof HttpErrorResponse) {
    const body = err.error;
    if (body && typeof body === 'object' && 'errorCode' in body) {
      return body as ApiError;
    }
    if (err.status === 0) {
      return { status: 0, errorCode: 'NETWORK', message: 'Cannot reach the server. Check your connection and try again.' };
    }
    return { status: err.status, errorCode: 'HTTP_' + err.status, message: 'Something went wrong. Please try again.' };
  }
  if (err && typeof err === 'object' && 'errorCode' in err) {
    return err as ApiError;
  }
  return { status: 0, errorCode: 'UNKNOWN', message: 'Something went wrong. Please try again.' };
}

/** User-safe message, with correlation ID suffix for support. */
export function errorMessage(err: unknown): string {
  const e = toApiError(err);
  const ref = e.correlationId ? ` (ref ${e.correlationId})` : '';
  return e.message + (e.status >= 500 ? ref : '');
}

/** Maps ApiError.fieldErrors onto matching form controls. Returns unmatched messages. */
export function applyFieldErrors(form: FormGroup, err: unknown): string[] {
  const e = toApiError(err);
  const rest: string[] = [];
  for (const fe of e.fieldErrors ?? []) {
    const control: AbstractControl | null = form.get(fe.field);
    if (control) {
      control.setErrors({ ...(control.errors ?? {}), server: fe.message });
      control.markAsTouched();
    } else {
      rest.push(fe.message);
    }
  }
  return rest;
}

/** First human-readable message for a control, or null. */
export function controlMessage(control: AbstractControl | null | undefined, label: string): string | null {
  if (!control || !control.touched || !control.errors) return null;
  const errs = control.errors;
  if (errs['server']) return String(errs['server']);
  if (errs['required']) return `${label} is required`;
  if (errs['email']) return 'Enter a valid email address';
  if (errs['minlength']) return `${label} must be at least ${errs['minlength'].requiredLength} characters`;
  if (errs['maxlength']) return `${label} is too long`;
  if (errs['pattern']) return `${label} is not in a valid format`;
  if (errs['passwordPolicy']) return 'Use 10+ characters with upper, lower, a digit and a symbol';
  if (errs['mismatch']) return 'Passwords do not match';
  if (errs['min']) return `${label} is too small`;
  return `${label} is invalid`;
}
