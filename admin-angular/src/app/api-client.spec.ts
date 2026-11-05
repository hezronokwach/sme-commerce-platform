import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Api } from './api/api';
import { provideApiConfiguration } from './api/api-configuration';
import { productList } from './api/fn/product/product-list';
import { ProductResponse } from './api/models/product-response';

/**
 * The generated client sends the request the backend expects. No real server: HttpTestingController
 * catches the request so the test can look at it and send back a made-up answer.
 */
describe('generated API client', () => {
  let api: Api;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideApiConfiguration('')],
    });
    api = TestBed.inject(Api);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    // Fails the test if a request was sent that the test didn't expect.
    http.verify();
  });

  it('lists products with GET /api/admin/products and the status filter', async () => {
    const answer = api.invoke(productList, { status: 'ACTIVE' });

    const request = http.expectOne('/api/admin/products?status=ACTIVE');
    expect(request.request.method).toBe('GET');
    const oneProduct: ProductResponse = { name: 'Sukuma bundle', priceMinor: 5000, currency: 'KES' };
    request.flush([oneProduct]);

    const products = await answer;
    expect(products.length).toBe(1);
    expect(products[0].name).toBe('Sukuma bundle');
  });
});
