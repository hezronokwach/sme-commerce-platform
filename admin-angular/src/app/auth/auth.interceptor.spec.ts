import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { vi } from 'vitest';
import { provideApiConfiguration } from '../api/api-configuration';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

/**
 * The interceptor with the real AuthService. HttpTestingController plays the backend: each test sees
 * the requests the app sends and decides what to answer.
 */
describe('authInterceptor', () => {
  let httpClient: HttpClient;
  let backend: HttpTestingController;
  let auth: AuthService;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideApiConfiguration(''),
        provideRouter([]),
      ],
    });
    httpClient = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    // Fails the test if the app sent a request the test didn't answer.
    backend.verify();
  });

  it('adds the access token to admin requests', async () => {
    await logInWithToken('token-1');

    httpClient.get('/api/admin/products').subscribe();

    const request = backend.expectOne('/api/admin/products');
    expect(request.request.headers.get('Authorization')).toBe('Bearer token-1');
    request.flush([]);
  });

  it('does not add a token to login, refresh or logout', async () => {
    await logInWithToken('token-1');

    httpClient.post('/api/auth/logout', null).subscribe();

    const request = backend.expectOne('/api/auth/logout');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush(null);
  });

  it('on a 401 it refreshes once and sends the request again with the new token', async () => {
    await logInWithToken('old-token');
    const answer = firstValueFrom(httpClient.get<string[]>('/api/admin/products'));

    backend.expectOne('/api/admin/products').flush(null, { status: 401, statusText: 'Unauthorized' });
    await letPromisesRun();
    backend.expectOne('/api/auth/refresh').flush({ accessToken: 'new-token', expiresIn: 900 });
    await letPromisesRun();

    const retry = backend.expectOne('/api/admin/products');
    expect(retry.request.headers.get('Authorization')).toBe('Bearer new-token');
    retry.flush(['Sukuma']);
    expect(await answer).toEqual(['Sukuma']);
  });

  it('two 401s at the same moment share one refresh', async () => {
    await logInWithToken('old-token');
    const first = firstValueFrom(httpClient.get('/api/admin/products'));
    const second = firstValueFrom(httpClient.get('/api/admin/categories'));

    backend.expectOne('/api/admin/products').flush(null, { status: 401, statusText: 'Unauthorized' });
    backend.expectOne('/api/admin/categories').flush(null, { status: 401, statusText: 'Unauthorized' });
    await letPromisesRun();

    // expectOne fails if there were two refresh requests. A second one would reuse the rotated
    // refresh token, and the backend would treat that as theft and log the user out everywhere.
    backend.expectOne('/api/auth/refresh').flush({ accessToken: 'new-token', expiresIn: 900 });
    await letPromisesRun();

    backend.expectOne('/api/admin/products').flush([]);
    backend.expectOne('/api/admin/categories').flush([]);
    await first;
    await second;
  });

  it('when the refresh fails too, it goes to the login page', async () => {
    const navigate = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    await logInWithToken('old-token');
    const answer = firstValueFrom(httpClient.get('/api/admin/products'));

    backend.expectOne('/api/admin/products').flush(null, { status: 401, statusText: 'Unauthorized' });
    await letPromisesRun();
    backend.expectOne('/api/auth/refresh').flush(null, { status: 401, statusText: 'Unauthorized' });

    await expect(answer).rejects.toBeTruthy();
    expect(navigate).toHaveBeenCalledWith('/login');
    expect(auth.isLoggedIn()).toBe(false);
  });

  it('other errors reach the page unchanged, without a refresh', async () => {
    await logInWithToken('token-1');
    const answer = firstValueFrom(httpClient.get('/api/admin/products/123'));

    backend.expectOne('/api/admin/products/123').flush(null, { status: 404, statusText: 'Not Found' });

    await expect(answer).rejects.toMatchObject({ status: 404 });
    backend.expectNone('/api/auth/refresh');
  });

  /** Logs in through the real AuthService, with the fake backend answering the login request. */
  async function logInWithToken(token: string): Promise<void> {
    const loggingIn = auth.login('owner@example.com', 'a-long-password');
    backend.expectOne('/api/auth/login').flush({ accessToken: token, expiresIn: 900 });
    await loggingIn;
  }

  /** The refresh and retry happen in Promise callbacks; this lets them run before the next check. */
  function letPromisesRun(): Promise<void> {
    return new Promise(function (resolve) {
      setTimeout(resolve, 0);
    });
  }
});
