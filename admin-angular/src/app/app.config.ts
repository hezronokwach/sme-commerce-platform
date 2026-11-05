import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { provideApiConfiguration } from './api/api-configuration';
import { environment } from '../environments/environment';
import { routes } from './app.routes';

/** App-wide setup: what every page and service can use. */
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    // Angular's HTTP client, which the generated API functions use to send requests.
    provideHttpClient(),
    // Where the generated API client sends its requests: '' (same address) in development.
    provideApiConfiguration(environment.apiBaseUrl),
  ],
};
