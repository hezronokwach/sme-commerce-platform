import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideApiConfiguration } from '../api/api-configuration';
import { ProductsPage, buildRows } from './products-page';

describe('buildRows', () => {
  it('shows the category name and the price in shillings', () => {
    const rows = buildRows(
      [{ id: 'p1', name: 'Sukuma bundle', priceMinor: 5000, currency: 'KES', categoryId: 'c1', status: 'ACTIVE' }],
      [{ id: 'c1', name: 'Vegetables' }],
    );

    expect(rows).toEqual([
      { id: 'p1', name: 'Sukuma bundle', price: 'KES 50.00', categoryName: 'Vegetables', status: 'ACTIVE' },
    ]);
  });

  it('shows a dash for a product without a category', () => {
    const rows = buildRows([{ id: 'p1', name: 'Mandazi', priceMinor: 2000, currency: 'KES' }], []);

    expect(rows[0].categoryName).toBe('—');
  });
});

describe('ProductsPage', () => {
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ProductsPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideApiConfiguration(''), provideRouter([])],
    });
    backend = TestBed.inject(HttpTestingController);
  });

  it('loads the products and shows one table row each', async () => {
    const fixture = TestBed.createComponent(ProductsPage);
    fixture.detectChanges();

    backend.expectOne('/api/admin/products').flush([
      { id: 'p1', name: 'Sukuma bundle', priceMinor: 5000, currency: 'KES', status: 'ACTIVE' },
      { id: 'p2', name: 'Chapati', priceMinor: 3000, currency: 'KES', status: 'DRAFT' },
    ]);
    await letPromisesRun();
    backend.expectOne('/api/admin/categories').flush([]);
    await letPromisesRun();
    fixture.detectChanges();

    const page = fixture.nativeElement as HTMLElement;
    const rows = page.querySelectorAll('tbody tr');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('Sukuma bundle');
    expect(rows[0].textContent).toContain('KES 50.00');
    backend.verify();
  });

  it('says so when the shop has no products', async () => {
    const fixture = TestBed.createComponent(ProductsPage);
    fixture.detectChanges();

    backend.expectOne('/api/admin/products').flush([]);
    await letPromisesRun();
    backend.expectOne('/api/admin/categories').flush([]);
    await letPromisesRun();
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('No products yet.');
  });

  function letPromisesRun(): Promise<void> {
    return new Promise(function (resolve) {
      setTimeout(resolve, 0);
    });
  }
});
