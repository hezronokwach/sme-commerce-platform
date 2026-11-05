import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { provideApiConfiguration } from './api/api-configuration';
import { authInterceptor } from './auth/auth.interceptor';
import { AuthService } from './auth/auth.service';
import { environment } from '../environments/environment';
import { routes } from './app.routes';

/**
 * Runs once before the first page opens. The access token is only kept in memory, so a page reload
 * loses it; asking for a new one with the refresh cookie keeps the user logged in. If there is no
 * valid cookie, refresh() answers false and the guard shows the login page.
 */
async function restoreSession(): Promise<void> {
  const auth = inject(AuthService);
  await auth.refresh();
}

/** App-wide setup: what every page and service can use. */
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    // Angular's HTTP client. Every request passes through authInterceptor (adds the token, retries on 401).
    provideHttpClient(withInterceptors([authInterceptor])),
    // Where the generated API client sends its requests: '' (same address) in development.
    provideApiConfiguration(environment.apiBaseUrl),
    provideAppInitializer(restoreSession),
  ],
};
