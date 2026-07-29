import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * Client-side gate only — real enforcement is the gateway JWT filter and
 * service-level checks. Guards exist for UX, not security.
 */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isAuthenticated()) {
    return true;
  }
  // Attempt session restore (page reload) before bouncing visitors to the
  // public landing page — an unauthenticated arrival is a guest, not a failure.
  return auth.refresh().pipe(
    map(user => user ? true : router.createUrlTree(['/welcome']))
  );
};
