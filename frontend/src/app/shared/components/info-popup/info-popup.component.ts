import { Component, inject } from '@angular/core';
import { InfoService } from '../../../core/info/info.service';

/** Renders the popups queued in InfoService. Mounted once in the root component. */
@Component({
  selector: 'app-info-popup',
  templateUrl: './info-popup.component.html',
  styleUrl: './info-popup.component.css',
})
export class InfoPopupComponent {
  protected readonly info = inject(InfoService);
}
