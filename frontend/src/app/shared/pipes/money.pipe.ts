import { Pipe, PipeTransform, inject } from '@angular/core';
import { SiteConfigService } from '../../core/site-config.service';

@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  private readonly site = inject(SiteConfigService);

  transform(value: number | null | undefined, currency?: string | null): string {
    if (value === null || value === undefined || Number.isNaN(Number(value))) return '';
    const code = currency || this.site.config().currency || 'INR';
    try {
      return new Intl.NumberFormat('en-IN', { style: 'currency', currency: code }).format(Number(value));
    } catch {
      return `${code} ${Number(value).toFixed(2)}`;
    }
  }
}
