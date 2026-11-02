import { HttpClient } from '@angular/common/http';
import { DOCUMENT } from '@angular/common';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, of, tap } from 'rxjs';
import { SiteConfig } from '../shared/models/storefront.models';

export const DEFAULT_SITE_CONFIG: SiteConfig = {
  theme: { bg: '#faf8f5', surface: '#ffffff', text: '#1f1d1a', accent: '#8a6d3b', font: 'MIXED' },
  shop: { displayName: 'Jewel Store', tagline: null, logoUrl: null, supportEmail: null, supportPhone: null, address: null },
  headerLinks: [],
  footerLinks: [],
  currency: 'INR',
  paymentMethods: ['COD'],
  mockPayments: false,
  imageRatio: '1:1',
  cartMaxQty: 10,
};

const HEX = /^#[0-9a-fA-F]{3,8}$/;

/** Loads storefront config from the backend and applies theme tokens as CSS variables. */
@Injectable({ providedIn: 'root' })
export class SiteConfigService {
  private readonly http = inject(HttpClient);
  private readonly doc = inject(DOCUMENT);
  private readonly cfg = signal<SiteConfig>(DEFAULT_SITE_CONFIG);
  readonly config = this.cfg.asReadonly();
  readonly loadFailed = signal(false);

  load(): Observable<SiteConfig | null> {
    return this.http.get<SiteConfig>('/api/v1/storefront/config').pipe(
      tap((c) => {
        this.cfg.set({ ...DEFAULT_SITE_CONFIG, ...c });
        this.loadFailed.set(false);
        this.applyTheme(this.cfg());
      }),
      catchError(() => {
        this.loadFailed.set(true);
        this.applyTheme(DEFAULT_SITE_CONFIG);
        return of(null);
      }),
    );
  }

  applyTheme(c: SiteConfig): void {
    const style = this.doc.documentElement.style;
    const t = c.theme;
    const set = (name: string, value: string | undefined) => {
      if (value && HEX.test(value)) style.setProperty(name, value);
    };
    // Light-theme palette from Site settings. styles.css maps these onto --c-*; the dark theme ignores bg/surface/text
    // and derives a lifted accent from --cfg-accent.
    set('--cfg-bg', t.bg);
    set('--cfg-surface', t.surface);
    set('--cfg-text', t.text);
    set('--cfg-accent', t.accent);
    const serif = "'Cormorant Garamond', Georgia, serif";
    const sans = "'Inter', system-ui, -apple-system, 'Segoe UI', sans-serif";
    const font = String(t.font);
    style.setProperty('--font-heading', font === 'SANS' ? sans : serif);
    style.setProperty('--font-body', font === 'SERIF' ? serif : sans);
    const m = /^(\d+):(\d+)$/.exec(c.imageRatio ?? '');
    style.setProperty('--img-ratio', m ? `${m[1]} / ${m[2]}` : '1 / 1');
  }
}
