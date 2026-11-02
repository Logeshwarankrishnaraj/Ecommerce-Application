import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup } from '@angular/forms';
import { applyFieldErrors, errorMessage, toApiError } from './api-error.util';

describe('api error utilities', () => {
  it('passes through the server error body', () => {
    const err = new HttpErrorResponse({ status: 409, error: { status: 409, errorCode: 'PRICE_CHANGED', message: 'The price changed.' } });
    expect(toApiError(err).errorCode).toBe('PRICE_CHANGED');
    expect(errorMessage(err)).toBe('The price changed.');
  });

  it('gives a friendly message when the server is unreachable', () => {
    expect(toApiError(new HttpErrorResponse({ status: 0 })).errorCode).toBe('NETWORK');
  });

  it('never leaks raw bodies for unexpected responses', () => {
    const msg = errorMessage(new HttpErrorResponse({ status: 500, error: '<html>stack trace</html>' }));
    expect(msg).toBe('Something went wrong. Please try again.');
  });

  it('maps field errors onto controls and returns the rest', () => {
    const form = new FormGroup({ email: new FormControl('') });
    const err = new HttpErrorResponse({ status: 422, error: { status: 422, errorCode: 'VALIDATION_FAILED', message: 'x', fieldErrors: [{ field: 'email', message: 'Taken' }, { field: 'other', message: 'Nope' }] } });
    expect(applyFieldErrors(form, err)).toEqual(['Nope']);
    expect(form.controls['email'].errors).toEqual({ server: 'Taken' });
  });
});
