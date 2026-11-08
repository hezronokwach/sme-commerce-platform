import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { messageFrom } from '../shared/error-message';

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
      this.errorMessage.set(messageFrom(error, 'Login failed. Please try again.'));
    } finally {
      this.busy.set(false);
    }
  }
}

