import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideApiConfiguration } from '../api/api-configuration';
import { ProductFormPage } from './product-form-page';
import { parseWholeNumber } from './stock-section';

describe('ProductFormPage', () => {
  let backend: HttpTestingController;
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideApiConfiguration(''),
        provideRouter([{ path: 'products/:productId', component: ProductFormPage }]),
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    // A small app with just the router, so the page gets a real ":productId" from the address.
    harness = await RouterTestingHarness.create();
  });

  it('fills the form with the product, the price in shillings', async () => {
    await openSukuma();

    expect(input('price').value).toBe('50.50');
    expect(input('name').value).toBe('Sukuma bundle');
  });

  it('sends the version it loaded and the price in cents', async () => {
    await openSukuma();

    typeInto('price', '55');
    submitForm();

    const save = backend.expectOne('/api/admin/products/p1');
    expect(save.request.method).toBe('PUT');
    expect(save.request.body.priceMinor).toBe(5500);
    expect(save.request.body.version).toBe(3);
  });

  it('says when someone else saved first, instead of overwriting their change', async () => {
    await openSukuma();

    submitForm();
    backend.expectOne('/api/admin/products/p1').flush(
      { status: 409, code: 'CONCURRENT_UPDATE', detail: 'Changed by someone else' },
      { status: 409, statusText: 'Conflict' },
    );
    await letPromisesRun();
    harness.detectChanges();

    expect(pageText()).toContain('Someone else changed this product');
  });

  it('does not send a price it cannot read', async () => {
    await openSukuma();

    typeInto('price', '5.123');
    submitForm();
    await letPromisesRun();
    harness.detectChanges();

    backend.expectNone('/api/admin/products/p1');
    expect(pageText()).toContain('Enter the price in shillings');
  });

  /** Opens /products/p1 and answers the page's requests (categories, product, stock). */
  async function openSukuma(): Promise<void> {
    await harness.navigateByUrl('/products/p1', ProductFormPage);

    backend.expectOne('/api/admin/categories').flush([{ id: 'c1', name: 'Vegetables' }]);
    await letPromisesRun();
    backend.expectOne('/api/admin/products/p1').flush({
      id: 'p1', name: 'Sukuma bundle', priceMinor: 5050, currency: 'KES', categoryId: 'c1',
      status: 'ACTIVE', version: 3,
    });
    await letPromisesRun();
    harness.detectChanges();

    // The stock section under the form asks for the stock once it is shown.
    backend.expectOne('/api/admin/stock/p1').flush({ productId: 'p1', onHand: 10, reserved: 2, available: 8 });
    await letPromisesRun();
    harness.detectChanges();
    // ngModel writes the loaded values into the inputs a moment later.
    await harness.fixture.whenStable();
  }

  function input(name: string): HTMLInputElement {
    return harness.routeNativeElement!.querySelector('input[name="' + name + '"]') as HTMLInputElement;
  }

  function typeInto(name: string, text: string): void {
    const box = input(name);
    box.value = text;
    // ngModel listens for "input" events, the same ones a keyboard sends.
    box.dispatchEvent(new Event('input'));
  }

  function submitForm(): void {
    const form = harness.routeNativeElement!.querySelector('form.form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit'));
  }

  function pageText(): string {
    return harness.routeNativeElement!.textContent || '';
  }

  function letPromisesRun(): Promise<void> {
    return new Promise(function (resolve) {
      setTimeout(resolve, 0);
    });
  }
});

describe('parseWholeNumber', () => {
  it('reads positive and negative whole numbers', () => {
    expect(parseWholeNumber('20')).toBe(20);
    expect(parseWholeNumber(' -3 ')).toBe(-3);
  });

  it('rejects fractions, text and empty input', () => {
    expect(parseWholeNumber('2.5')).toBeNull();
    expect(parseWholeNumber('abc')).toBeNull();
    expect(parseWholeNumber('')).toBeNull();
  });
});
