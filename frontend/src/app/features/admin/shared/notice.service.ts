import { Injectable, inject } from '@angular/core';
import { InfoService } from '../../../core/info/info.service';

/** Kept so existing admin screens keep working; all feedback is shown through the global InfoService popup. */
@Injectable({ providedIn: 'root' })
export class NoticeService {
  private readonly popup = inject(InfoService);
  success(text: string): void { this.popup.success(text); }
  info(text: string): void { this.popup.info(text); }
  error(text: string): void { this.popup.error(text); }
}
