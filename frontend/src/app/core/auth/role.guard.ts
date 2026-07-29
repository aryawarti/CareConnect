import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/** Route guard for role-gated features. UX only — the server is the enforcer. */
export const roleGuard = (...roles: string[]): CanActivateFn => () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const user = auth.user();
  return user && roles.some(r => user.roles.includes(r))
    ? true
    : router.createUrlTree(['/']);
};
