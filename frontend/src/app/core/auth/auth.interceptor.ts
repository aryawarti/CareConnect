import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';

const AUTH_ENDPOINTS = ['/api/auth/login', '/api/auth/register', '/api/auth/refresh'];

/**
 * Attaches the bearer token; on a 401 for a non-auth call, tries one silent
 * refresh (rotation-safe: the backend hands out a new pair) and replays the
 * request. A failed refresh clears the session and lands on /login.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  const withToken = auth.accessToken
    ? req.clone({ setHeaders: { Authorization: `Bearer ${auth.accessToken}` } })
    : req;

  return next(withToken).pipe(
    catchError((error: HttpErrorResponse) => {
      const isAuthCall = AUTH_ENDPOINTS.some(url => req.url.includes(url));
      if (error.status !== 401 || isAuthCall) {
        return throwError(() => error);
      }
      return auth.refresh().pipe(
        // Only a FAILED REFRESH ends the session. A replayed request that
        // still 401s is a server-side authorization problem, not proof that
        // the session is dead — logging out there hides the real bug behind a
        // login redirect (it hid a gateway header-stripping bug for a phase).
        catchError(refreshError => {
          auth.clearSession();
          router.navigate(['/login']);
          return throwError(() => refreshError);
        }),
        switchMap(user => {
          if (!user) {
            auth.clearSession();
            router.navigate(['/login']);
            return throwError(() => error);
          }
          return next(req.clone({
            setHeaders: { Authorization: `Bearer ${auth.accessToken}` }
          }));
        })
      );
    })
  );
};
