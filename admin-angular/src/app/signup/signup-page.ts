import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { environment } from '../../environments/environment';
import { AuthService } from '../auth/auth.service';
import { messageFrom } from '../shared/error-message';
import { isValidWebName, webNameFrom } from './web-name';

/** The smallest password the backend accepts (bcrypt reads at most 72 bytes, so that's the top). */
const PASSWORD_MIN = 10;

/**
 * Sign-up at /signup: creates the shop and its owner in one step, then opens the product list,
 * already logged in. The web name is suggested from the shop name until the owner edits it.
 */
@Component({
  selector: 'app-signup-page',
  imports: [FormsModule, RouterLink],
  templateUrl: './signup-page.html',
})
export class SignupPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly homeUrl = environment.storefrontUrl + '/';
  protected readonly storefrontUrl = environment.storefrontUrl;

  shopName = '';
  webName = '';
  ownerName = '';
  email = '';
  password = '';

  /** Once the owner types their own web name, the shop name stops changing it. */
  private webNameEdited = false;

  protected readonly errorMessage = signal<string | null>(null);
  protected readonly busy = signal(false);

  shopNameChanged(name: string): void {
    this.shopName = name;
    if (!this.webNameEdited) {
      this.webName = webNameFrom(name);
    }
  }

  webNameChanged(name: string): void {
    this.webName = name;
    this.webNameEdited = true;
  }

  async signUp(): Promise<void> {
    this.errorMessage.set(null);

    // The same rules as the backend, checked here first so the owner gets a clear message at once.
    const problem = this.problemWithForm();
    if (problem !== null) {
      this.errorMessage.set(problem);
      return;
    }

    this.busy.set(true);
    try {
      await this.auth.signUp({
        shopName: this.shopName.trim(),
        shopSlug: this.webName,
        ownerName: this.ownerName.trim(),
        email: this.email.trim(),
        password: this.password,
      });
      await this.router.navigateByUrl('/products');
    } catch (error) {
      this.errorMessage.set(messageFrom(error, 'Sign-up failed. Please try again.'));
    } finally {
      this.busy.set(false);
    }
  }

  private problemWithForm(): string | null {
    if (this.shopName.trim() === '' || this.ownerName.trim() === '' || this.email.trim() === '') {
      return 'Fill in every field.';
    }
    if (!isValidWebName(this.webName)) {
      return 'The web name needs 3 to 50 lowercase letters, digits or hyphens.';
    }
    if (this.password.length < PASSWORD_MIN) {
      return 'The password needs at least ' + PASSWORD_MIN + ' characters.';
    }
    return null;
  }
}
