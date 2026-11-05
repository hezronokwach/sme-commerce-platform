import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';

/** The login form at /login. On success it opens the product list. */
@Component({
  selector: 'app-login-page',
  imports: [FormsModule],
  templateUrl: './login-page.html',
  styleUrl: './login-page.css',
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  // Bound to the two inputs with [(ngModel)], so they always hold what the user typed.
  email = '';
  password = '';

  /** The message shown under the form, or null when there is none. */
  protected readonly errorMessage = signal<string | null>(null);

  /** True while the login request is on its way, so the button can't be clicked twice. */
  protected readonly busy = signal(false);

  async logIn(): Promise<void> {
    this.errorMessage.set(null);
    this.busy.set(true);
    try {
      await this.auth.login(this.email, this.password);
      await this.router.navigateByUrl('/products');
    } catch (error) {
      this.errorMessage.set(messageFrom(error));
    } finally {
      this.busy.set(false);
    }
  }
}

/**
 * Every backend error has the same JSON shape (ADR 0006); its "detail" field is written for people,
 * e.g. "The email or password is incorrect.". Use it when it's there.
 */
function messageFrom(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return 'The server cannot be reached. Is the backend running?';
    }
    const body = error.error;
    if (body !== null && typeof body === 'object' && typeof body.detail === 'string') {
      return body.detail;
    }
  }
  return 'Login failed. Please try again.';
}
