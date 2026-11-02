import { FormControl } from '@angular/forms';
import { passwordPolicy, safeReturnUrl } from './validators';

describe('passwordPolicy', () => {
  it('accepts a strong password', () => expect(passwordPolicy(new FormControl('Str0ng!Passw0rd'))).toBeNull());
  it('rejects short, or missing classes', () => {
    for (const bad of ['Sh0rt!', 'alllowercase1!', 'ALLUPPERCASE1!', 'NoDigitsHere!!', 'NoSymbols12345']) {
      expect(passwordPolicy(new FormControl(bad))).toEqual({ passwordPolicy: true });
    }
  });
  it('ignores empty values (required handles those)', () => expect(passwordPolicy(new FormControl(''))).toBeNull());
});

describe('safeReturnUrl', () => {
  it('allows same-site paths', () => expect(safeReturnUrl('/account/orders')).toBe('/account/orders'));
  it('blocks open redirects', () => {
    expect(safeReturnUrl('https://evil.example')).toBe('/');
    expect(safeReturnUrl('//evil.example')).toBe('/');
    expect(safeReturnUrl(null)).toBe('/');
  });
});
