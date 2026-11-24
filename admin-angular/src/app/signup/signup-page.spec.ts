import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideApiConfiguration } from '../api/api-configuration';
import { AuthService } from '../auth/auth.service';
import { SignupPage } from './signup-page';

/** Stands in for the product list, so the test can see where sign-up leads. */
@Component({ template: 'Products' })
class FakeProductsPage {}

describe('SignupPage', () => {
  let backend: HttpTestingController;
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideApiConfiguration(''),
        provideRouter([
          { path: 'signup', component: SignupPage },
          { path: 'products', component: FakeProductsPage },
        ]),
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/signup');
  });

  it('suggests the web name from the shop name until the owner edits it', async () => {
    typeInto('shopName', 'Kamau Hardware');
    await showChanges();
    expect(input('webName').value).toBe('kamau-hardware');

    typeInto('webName', 'kamau-tools');
    typeInto('shopName', 'Kamau Hardware Ltd');
    await showChanges();
    expect(input('webName').value).toBe('kamau-tools');
  });

  it('creates the shop, logs the owner in and opens the products', async () => {
    fillTheForm();
    submitForm();

    const request = backend.expectOne('/api/auth/signup');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      shopName: 'Kamau Hardware',
      shopSlug: 'kamau-hardware',
      ownerName: 'Jane Kamau',
      email: 'jane@example.com',
      password: 'a-long-password',
    });
    request.flush({ accessToken: 'header.payload.signature', expiresIn: 900 });
    await letPromisesRun();

    expect(TestBed.inject(AuthService).isLoggedIn()).toBe(true);
    expect(TestBed.inject(Router).url).toBe('/products');
  });

  it("shows the backend's reason when the web name is taken", async () => {
    fillTheForm();
    submitForm();

    backend.expectOne('/api/auth/signup').flush(
      { status: 409, code: 'SLUG_TAKEN', detail: 'That web name is taken' },
      { status: 409, statusText: 'Conflict' },
    );
    await letPromisesRun();
    harness.detectChanges();

    expect(pageText()).toContain('That web name is taken');
    expect(TestBed.inject(Router).url).toBe('/signup');
  });

  it('refuses a short password before asking the backend', () => {
    fillTheForm();
    typeInto('password', 'short');
    submitForm();
    harness.detectChanges();

    backend.expectNone('/api/auth/signup');
    expect(pageText()).toContain('at least 10 characters');
  });

  function fillTheForm(): void {
    typeInto('shopName', 'Kamau Hardware');
    typeInto('ownerName', 'Jane Kamau');
    typeInto('email', 'jane@example.com');
    typeInto('password', 'a-long-password');
  }

  function input(name: string): HTMLInputElement {
    return harness.routeNativeElement!.querySelector('input[name="' + name + '"]') as HTMLInputElement;
  }

  function typeInto(name: string, text: string): void {
    const box = input(name);
    box.value = text;
    // ngModel listens for "input" events, the same ones a keyboard sends.
    box.dispatchEvent(new Event('input'));
    harness.detectChanges();
  }

  function submitForm(): void {
    const form = harness.routeNativeElement!.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit'));
  }

  function pageText(): string {
    return harness.routeNativeElement!.textContent || '';
  }

  /** ngModel writes a new value into its input a moment later, not at once. */
  async function showChanges(): Promise<void> {
    await harness.fixture.whenStable();
    harness.detectChanges();
  }

  function letPromisesRun(): Promise<void> {
    return new Promise(function (resolve) {
      setTimeout(resolve, 0);
    });
  }
});
