import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Put on a route with canActivate: [authGuard]. Before the router opens that page it asks this
 * function: true lets the user in, a UrlTree sends them somewhere else (here: the login page).
 *
 * This only hides pages. The real protection is the backend, which rejects every admin request
 * without a valid token.
 */
export const authGuard: CanActivateFn = function (): boolean | UrlTree {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isLoggedIn()) {
    return true;
  }
  return router.parseUrl('/login');
};
