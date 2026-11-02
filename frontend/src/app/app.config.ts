import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withFetch, withInterceptors, withXsrfConfiguration } from '@angular/common/http';
import { provideRouter, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { routes } from './app.routes';
import { AuthService } from './core/auth/auth.service';
import { SiteConfigService } from './core/site-config.service';
import { apiErrorInterceptor } from './core/http/api-error.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding(), withInMemoryScrolling({ scrollPositionRestoration: 'enabled' })),
    // Angular reads the XSRF-TOKEN cookie and echoes it as X-XSRF-TOKEN on mutating same-origin requests.
    provideHttpClient(withFetch(), withInterceptors([apiErrorInterceptor]), withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' })),
    // Prime CSRF cookie, restore the server session and load theme/shop config before first render.
    provideAppInitializer(async () => {
      const auth = inject(AuthService);
      const site = inject(SiteConfigService);
      await firstValueFrom(auth.init());
      await Promise.all([firstValueFrom(auth.restoreSession()), firstValueFrom(site.load())]);
    }),
  ],
};
