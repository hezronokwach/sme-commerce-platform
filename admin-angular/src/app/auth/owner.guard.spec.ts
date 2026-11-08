import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { ownerGuard } from './owner.guard';

describe('ownerGuard', () => {
  let owner: boolean;

  beforeEach(() => {
    owner = false;
    // A stand-in AuthService: the guard only asks isOwner().
    const fakeAuth = {
      isOwner: function (): boolean {
        return owner;
      },
    };
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: fakeAuth }],
    });
  });

  it('sends staff to the products page', () => {
    const answer = runGuard();

    expect(answer instanceof UrlTree).toBe(true);
    const router = TestBed.inject(Router);
    expect(router.serializeUrl(answer as UrlTree)).toBe('/products');
  });

  it('lets an owner in', () => {
    owner = true;

    expect(runGuard()).toBe(true);
  });

  function runGuard(): unknown {
    return TestBed.runInInjectionContext(function () {
      return ownerGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot);
    });
  }
});
