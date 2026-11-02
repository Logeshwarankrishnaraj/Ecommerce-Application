import { TestBed } from '@angular/core/testing';
import { THEME_KEY, ThemeService } from './theme.service';

describe('ThemeService', () => {
  beforeEach(() => { localStorage.removeItem(THEME_KEY); document.documentElement.removeAttribute('data-theme'); });
  afterEach(() => localStorage.removeItem(THEME_KEY));

  it('applies an explicit dark choice and persists it', () => {
    const s = TestBed.inject(ThemeService);
    s.set('dark');
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    expect(localStorage.getItem(THEME_KEY)).toBe('dark');
    expect(s.effective()).toBe('dark');
  });

  it('applies light and restores a stored choice', () => {
    localStorage.setItem(THEME_KEY, 'light');
    const s = TestBed.inject(ThemeService);
    expect(s.mode()).toBe('light');
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
  });

  it('defaults to system and resolves to light or dark', () => {
    const s = TestBed.inject(ThemeService);
    expect(s.mode()).toBe('system');
    expect(['light', 'dark']).toContain(document.documentElement.getAttribute('data-theme'));
  });
});
