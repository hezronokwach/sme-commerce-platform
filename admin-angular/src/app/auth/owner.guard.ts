import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Put on routes only owners may open (Settings). Staff are sent to the products page instead.
 *
 * Like authGuard this only hides pages: the backend's own @PreAuthorize("hasRole('OWNER')") is what
 * really stops a staff member from inviting staff or changing the shop.
 */
export const ownerGuard: CanActivateFn = function (): boolean | UrlTree {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isOwner()) {
    return true;
  }
  return router.parseUrl('/products');
};
