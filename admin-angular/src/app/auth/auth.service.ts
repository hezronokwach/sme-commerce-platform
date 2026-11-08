import { Injectable, inject, signal } from '@angular/core';
import { Api } from '../api/api';
import { authLogin } from '../api/fn/auth/auth-login';
import { authLogout } from '../api/fn/auth/auth-logout';
import { authRefresh } from '../api/fn/auth/auth-refresh';
import { TokenResponse } from '../api/models/token-response';
import { roleFromToken } from './token-role';

/**
 * Who is logged in, and the access token that proves it.
 *
 * The access token lives only in memory (a signal), never in localStorage: any script on the page can
 * read localStorage, so an XSS bug could steal it from there. The price is that a page reload forgets
 * it, so on start-up the app calls refresh(): the refresh token is in an httpOnly cookie the browser
 * sends by itself, and the backend answers with a new access token.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(Api);

  /** The current access token, or null when logged out. A signal, so pages update when it changes. */
  private readonly accessToken = signal<string | null>(null);

  /** The refresh call that is running right now, if any (see refresh()). */
  private refreshInProgress: Promise<boolean> | null = null;

  getAccessToken(): string | null {
    return this.accessToken();
  }

  isLoggedIn(): boolean {
    return this.accessToken() !== null;
  }

  /**
   * The logged-in user's role: 'OWNER' or 'STAFF', or null when logged out. Read from the token
   * itself, so it is always the role of the token we are using (also right after a refresh).
   */
  getRole(): string | null {
    const token = this.accessToken();
    if (token === null) {
      return null;
    }
    return roleFromToken(token);
  }

  /** Owners may open Settings (staff, shop profile); staff may not. */
  isOwner(): boolean {
    return this.getRole() === 'OWNER';
  }

  /**
   * Logs in. Throws the HTTP error if the backend says no (wrong password, locked account, ...);
   * the login page shows the error's message.
   */
  async login(email: string, password: string): Promise<void> {
    const tokens = await this.api.invoke(authLogin, { body: { email: email, password: password } });
    this.rememberToken(tokens);
  }

  /**
   * Gets a new access token with the refresh cookie. Returns true if that worked, false if the user
   * must log in again.
   *
   * Only one refresh may run at a time. Each refresh token works once (the backend rotates it), and
   * using one twice looks like theft, so the backend logs the user out everywhere. If three requests
   * get a 401 at the same moment, they must all wait for the same refresh instead of starting three.
   */
  refresh(): Promise<boolean> {
    if (this.refreshInProgress === null) {
      this.refreshInProgress = this.refreshOnce();
    }
    return this.refreshInProgress;
  }

  /** Ends the session on the server too (the backend revokes the refresh token and clears the cookie). */
  async logout(): Promise<void> {
    try {
      await this.api.invoke(authLogout);
    } catch {
      // Even if the server can't be reached, the user is logged out here.
    }
    this.accessToken.set(null);
  }

  private async refreshOnce(): Promise<boolean> {
    try {
      const tokens = await this.api.invoke(authRefresh);
      this.rememberToken(tokens);
      return true;
    } catch {
      // No cookie, or it expired or was revoked: logged out.
      this.accessToken.set(null);
      return false;
    } finally {
      this.refreshInProgress = null;
    }
  }

  private rememberToken(tokens: TokenResponse): void {
    if (tokens.accessToken === undefined) {
      this.accessToken.set(null);
    } else {
      this.accessToken.set(tokens.accessToken);
    }
  }
}
