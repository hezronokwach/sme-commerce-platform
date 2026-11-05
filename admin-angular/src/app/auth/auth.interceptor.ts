import { HttpErrorResponse, HttpEvent, HttpHandlerFn, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, from, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * Runs for every HTTP request the app sends (registered in app.config.ts). It does two jobs:
 *
 * 1. Adds "Authorization: Bearer <access token>" so the backend knows who is calling.
 * 2. If the backend answers 401 (the 15-minute token expired), it refreshes the token once and sends
 *    the same request again. If refreshing fails too, the user goes back to the login page.
 *
 * Angular's HTTP client works with RxJS Observables (a stream that delivers the response later), so
 * this function uses a few RxJS helpers; each one is explained where it's used.
 */
export const authInterceptor: HttpInterceptorFn = function (
  request: HttpRequest<unknown>,
  next: HttpHandlerFn,
): Observable<HttpEvent<unknown>> {
  const auth = inject(AuthService);
  const router = inject(Router);

  // Login, sign-up, refresh and logout don't need a token, and a 401 from them must not trigger a
  // refresh (a failed refresh would otherwise try to refresh itself, forever).
  if (isAuthEndpoint(request)) {
    return next(request);
  }

  const firstTry = next(withToken(request, auth.getAccessToken()));

  // pipe(...) chains steps onto the response stream. catchError runs only if the request failed.
  return firstTry.pipe(
    catchError(function (error: unknown) {
      if (!isUnauthorized(error)) {
        // Any other error (404, 409, 500, ...) goes on to the page unchanged.
        return throwError(function () {
          return error;
        });
      }

      // from(...) turns the refresh Promise into a stream; switchMap then waits for its answer
      // (true or false) and continues with a new stream: the retried request, or the error.
      return from(auth.refresh()).pipe(
        switchMap(function (refreshed: boolean) {
          if (!refreshed) {
            router.navigateByUrl('/login');
            return throwError(function () {
              return error;
            });
          }
          // Same request again, now with the new token. If this fails too, the page sees that error.
          return next(withToken(request, auth.getAccessToken()));
        }),
      );
    }),
  );
};

function isAuthEndpoint(request: HttpRequest<unknown>): boolean {
  return request.url.includes('/api/auth/');
}

function isUnauthorized(error: unknown): boolean {
  if (error instanceof HttpErrorResponse) {
    return error.status === 401;
  }
  return false;
}

/** Requests can't be changed in place, so clone() makes a copy with the extra header. */
function withToken(request: HttpRequest<unknown>, token: string | null): HttpRequest<unknown> {
  if (token === null) {
    return request;
  }
  return request.clone({ setHeaders: { Authorization: 'Bearer ' + token } });
}
