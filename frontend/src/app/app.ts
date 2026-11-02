import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { InfoPopupComponent } from './shared/components/info-popup/info-popup.component';

import { ThemeService } from './core/theme/theme.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, InfoPopupComponent],
  template: '<router-outlet /><app-info-popup />',
})
export class App {
  // Instantiating the service applies the saved theme and follows OS changes.
  constructor() { inject(ThemeService); }
}
