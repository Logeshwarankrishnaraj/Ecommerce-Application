import { DOCUMENT } from '@angular/common';
import { Injectable, computed, inject, signal } from '@angular/core';

export type ThemeMode = 'light' | 'dark' | 'system';
export const THEME_KEY = 'theme-mode';

/** Light / dark / follow-the-device. One preference per browser, shared by the storefront and the admin panel. */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly doc = inject(DOCUMENT);
  private readonly query: MediaQueryList | null = this.doc.defaultView?.matchMedia?.('(prefers-color-scheme: dark)') ?? null;

  readonly mode = signal<ThemeMode>(this.read());
  private readonly systemDark = signal(this.query?.matches ?? false);
  /** The theme actually shown. */
  readonly effective = computed<'light' | 'dark'>(() => {
    const m = this.mode();
    return m === 'system' ? (this.systemDark() ? 'dark' : 'light') : m;
  });

  constructor() {
    this.query?.addEventListener?.('change', e => { this.systemDark.set(e.matches); this.apply(); });
    this.apply();
  }

  set(mode: ThemeMode): void {
    this.mode.set(mode);
    try { localStorage.setItem(THEME_KEY, mode); } catch { /* private mode: keep for this visit only */ }
    this.apply();
  }

  private read(): ThemeMode {
    try {
      const v = localStorage.getItem(THEME_KEY);
      if (v === 'light' || v === 'dark' || v === 'system') return v;
    } catch { /* storage blocked */ }
    return 'system';
  }

  private apply(): void {
    const root = this.doc.documentElement;
    root.setAttribute('data-theme', this.effective());
    this.doc.querySelector('meta[name="theme-color"]')?.setAttribute('content', this.effective() === 'dark' ? '#15120f' : '#faf6ee');
  }
}
