import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { Injector, inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { InfoService } from '../info/info.service';
import { newUuid } from '../../shared/util/validators';
import { toApiError } from '../../shared/util/api-error.util';
import { SESSION_EXPIRED_MESSAGE, isSessionExpiry, sessionExpiryTarget } from './session-expiry';

/**
 * Adds a correlation id to API calls and normalises every failure to ApiError
 * (user-safe message, never swallowed). When the server ends a signed-in session (401) the user is told, local state is
 * cleared, and pages that need a sign-in send them to it with a way back.
 */
export const apiErrorInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const injector = inject(Injector);
  const isApi = req.url.startsWith('/api/');
  const outgoing = isApi && !req.headers.has('X-Correlation-Id')
    ? req.clone({ setHeaders: { 'X-Correlation-Id': newUuid() } })
    : req;
  return next(outgoing).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse) {
        if (err.status === 401 && isApi && !req.url.includes('/auth/login')) {
          const expired = isSessionExpiry(err.status, req.url, auth.isLoggedIn());   // read before clearing
          auth.setUser(null);
          if (expired) {
            // Later failures from requests already in flight see a signed-out user, so this runs once.
            injector.get(InfoService).warn(SESSION_EXPIRED_MESSAGE);
            const router = injector.get(Router);
            const target = sessionExpiryTarget(router.url);
            if (target) void router.navigate(target.commands, { queryParams: { returnUrl: target.returnUrl } });
          }
        }
        return throwError(() => toApiError(err));
      }
      return throwError(() => err);
    }),
  );
};
