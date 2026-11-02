import { HttpErrorResponse } from '@angular/common/http';
import { AbstractControl, FormGroup } from '@angular/forms';
import { ApiError } from '../../../shared/models/api.models';

/** Extracts the stable API error body from an HttpErrorResponse (or builds a safe fallback). */
export function toApiError(err: unknown): ApiError {
  // The HTTP interceptor has usually already normalised the failure into an ApiError: keep its real message.
  if (!(err instanceof HttpErrorResponse) && err && typeof err === 'object' && typeof (err as ApiError).message === 'string' && 'errorCode' in err) {
    const a = err as ApiError;
    return { ...a, fieldErrors: a.fieldErrors ?? [] };
  }
  if (err instanceof HttpErrorResponse) {
    const body = err.error as Partial<ApiError> | null;
    if (body && typeof body === 'object' && typeof body.message === 'string') {
      return {
        status: body.status ?? err.status,
        errorCode: body.errorCode ?? 'ERROR',
        message: body.message,
        correlationId: body.correlationId,
        fieldErrors: body.fieldErrors ?? [],
        path: body.path,
      };
    }
    if (err.status === 0) {
      return { status: 0, errorCode: 'NETWORK', message: 'Cannot reach the server. Check your connection and retry.' };
    }
    if (err.status === 403) {
      return { status: 403, errorCode: 'ACCESS_DENIED', message: 'You do not have permission for this action.' };
    }
    return { status: err.status, errorCode: 'ERROR', message: 'Something went wrong. Please try again.' };
  }
  return { status: 0, errorCode: 'ERROR', message: 'Something went wrong. Please try again.' };
}

/** Human message including the correlation id (useful to quote to support). */
export function describeError(err: ApiError): string {
  // Field-level reasons that the page cannot highlight itself are appended so the user sees exactly what to fix.
  const extra = (err.fieldErrors ?? []).map((f) => f.message).filter((m) => m && !err.message.includes(m));
  const text = extra.length ? `${err.message} ${extra.join(' ')}` : err.message;
  return err.correlationId ? `${text} (ref ${err.correlationId})` : text;
}

/**
 * Maps API field errors onto matching form controls (sets a `server` error).
 * Returns messages that could not be mapped to a control so the caller can show them.
 */
export function applyFieldErrors(form: FormGroup, err: ApiError): string[] {
  const unmapped: string[] = [];
  for (const fe of err.fieldErrors ?? []) {
    const control: AbstractControl | null = form.get(fe.field);
    if (control) {
      control.setErrors({ ...(control.errors ?? {}), server: fe.message });
      control.markAsTouched();
    } else {
      unmapped.push(`${fe.field}: ${fe.message}`);
    }
  }
  return unmapped;
}

/** First user-facing message for a control (client validators or server error). */
export function controlMessage(control: AbstractControl | null, label = 'This field'): string | null {
  if (!control || !control.errors || !(control.touched || control.dirty)) {
    return null;
  }
  const e = control.errors;
  if (e['server']) return String(e['server']);
  if (e['required']) return `${label} is required.`;
  if (e['min']) return `${label} must be at least ${(e['min'] as { min: number }).min}.`;
  if (e['max']) return `${label} must be at most ${(e['max'] as { max: number }).max}.`;
  if (e['maxlength']) return `${label} is too long.`;
  if (e['pattern']) return `${label} has an invalid format.`;
  if (e['salePrice']) return 'Sale price must be between 0 and the base price.';
  return `${label} is invalid.`;
}
