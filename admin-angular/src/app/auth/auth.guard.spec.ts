import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthService } from './auth.service';

describe('authGuard', () => {
  let loggedIn: boolean;

  beforeEach(() => {
    loggedIn = false;
    // A stand-in AuthService: the guard only asks isLoggedIn().
    const fakeAuth = {
      isLoggedIn: function (): boolean {
        return loggedIn;
      },
    };
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: fakeAuth }],
    });
  });

  it('sends a logged-out user to /login', () => {
    const answer = runGuard();

    expect(answer instanceof UrlTree).toBe(true);
    const router = TestBed.inject(Router);
    expect(router.serializeUrl(answer as UrlTree)).toBe('/login');
  });

  it('lets a logged-in user in', () => {
    loggedIn = true;

    expect(runGuard()).toBe(true);
  });

  /** Guards use inject(), so they must run inside Angular's injection context. */
  function runGuard(): unknown {
    return TestBed.runInInjectionContext(function () {
      return authGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot);
    });
  }
});
