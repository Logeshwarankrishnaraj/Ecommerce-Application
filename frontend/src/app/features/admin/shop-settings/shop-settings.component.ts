import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SiteConfigService } from '../../../core/site-config.service';
import { ShopProfile, SiteSetting } from '../models/admin.models';
import { AdminApiService } from '../shared/admin-api.service';
import { describeError, toApiError } from '../shared/form-errors';
import { NoticeService } from '../shared/notice.service';
import { StateComponent } from '../shared/state.component';
import { InfoService } from '../../../core/info/info.service';

@Component({
  selector: 'admin-shop-settings',
  standalone: true,
  imports: [FormsModule, StateComponent],
  templateUrl: './shop-settings.component.html',
  styleUrl: './shop-settings.component.css',
})
export class ShopSettingsComponent implements OnInit {
  private readonly popup = inject(InfoService);
  private readonly api = inject(AdminApiService);
  private readonly notice = inject(NoticeService);
  private readonly site = inject(SiteConfigService);
  protected readonly profile = signal<ShopProfile | null>(null);
  protected readonly settings = signal<SiteSetting[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly profileError = signal<string | null>(null);
  protected readonly settingsError = signal<string | null>(null);
  protected profileValues: Record<string, string | number> = {};
  protected values: Record<string, string> = {};

  protected readonly profileFields: { key: keyof ShopProfile; label: string; type?: string }[] = [
    { key: 'displayName', label: 'Display name' }, { key: 'tagline', label: 'Tagline' }, { key: 'logoUrl', label: 'Logo URL (https:// or /path)' },
    { key: 'supportEmail', label: 'Support email', type: 'email' }, { key: 'supportPhone', label: 'Support phone' }, { key: 'senderDisplayName', label: 'Email sender name' },
    { key: 'addressLine1', label: 'Address line 1' }, { key: 'addressLine2', label: 'Address line 2' }, { key: 'city', label: 'City' }, { key: 'state', label: 'State' },
    { key: 'postalCode', label: 'Postal code' }, { key: 'country', label: 'Country' }, { key: 'returnAddress', label: 'Return address' },
    { key: 'handlingTimeDays', label: 'Handling time (days)', type: 'number' },
  ];

  ngOnInit(): void { this.load(); }
  protected groups(): string[] { return [...new Set(this.settings().map((s) => s.group))]; }
  protected byGroup(g: string): SiteSetting[] { return this.settings().filter((s) => s.group === g); }

  protected load(): void {
    this.loading.set(true); this.error.set(null);
    this.api.getShopProfile().subscribe({
      next: (p) => {
        this.profile.set(p); this.profileValues = { ...p } as unknown as Record<string, string | number>;
        this.api.listSettings().subscribe({
          next: (s) => { this.settings.set(s); this.values = Object.fromEntries(s.map((x) => [x.key, x.value])); this.loading.set(false); },
          error: (e) => { this.error.set(describeError(toApiError(e))); this.loading.set(false); },
        });
      },
      error: (e) => { this.error.set(describeError(toApiError(e))); this.loading.set(false); },
    });
  }

  protected saveProfile(): void {
    this.busy.set(true); this.profileError.set(null);
    const v = this.profileValues;
    const body = { ...(v as unknown as ShopProfile), handlingTimeDays: Number(v['handlingTimeDays'] ?? 2) };
    this.api.saveShopProfile(body).subscribe({
      next: (p) => { this.busy.set(false); this.profile.set(p); this.notice.success('Profile saved.'); this.site.load().subscribe(); },
      error: (e) => { this.busy.set(false); this.popup.error(describeError(toApiError(e))); },
    });
  }

  protected saveSettings(): void {
    this.busy.set(true); this.settingsError.set(null);
    this.api.saveSettings(this.values).subscribe({
      next: (s) => { this.busy.set(false); this.settings.set(s); this.notice.success('Settings saved.'); this.site.load().subscribe(); },
      error: (e) => { this.busy.set(false); this.popup.error(describeError(toApiError(e))); },
    });
  }
}
