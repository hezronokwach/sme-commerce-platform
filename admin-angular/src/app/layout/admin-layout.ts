import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../auth/auth.service';

/**
 * The frame around every logged-in page: the menu on top, the page below it.
 *
 * In app.routes.ts the logged-in pages are "children" of this layout, so the router shows them in
 * this component's own <router-outlet />, and the menu stays on screen while you move between pages.
 */
@Component({
  selector: 'app-admin-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './admin-layout.html',
  styleUrl: './admin-layout.css',
})
export class AdminLayout {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Settings is only in the menu for owners (ownerGuard also blocks the address itself). */
  protected showSettings(): boolean {
    return this.auth.isOwner();
  }

  /** "Owner" or "Staff", shown next to the log-out button. */
  protected roleLabel(): string {
    const role = this.auth.getRole();
    if (role === 'OWNER') {
      return 'Owner';
    }
    if (role === 'STAFF') {
      return 'Staff';
    }
    return '';
  }

  async logOut(): Promise<void> {
    await this.auth.logout();
    await this.router.navigateByUrl('/login');
  }
}
