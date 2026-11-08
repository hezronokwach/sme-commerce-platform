import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideApiConfiguration } from '../api/api-configuration';
import { OrderDetailPage, buildOrderView } from './order-detail-page';

const AMINAS_ORDER = {
  id: 'o1',
  orderNumber: 'ORD-0001',
  customerName: 'Amina',
  customerPhone: '254712345678',
  status: 'PENDING_PAYMENT',
  paymentMethod: 'MPESA',
  totalMinor: 10000,
  currency: 'KES',
  createdAt: '2026-11-08T07:00:00Z',
  expiresAt: '2026-11-08T07:15:00Z',
  lines: [{ productId: 'p1', productName: 'Sukuma bundle', unitPriceMinor: 5000, quantity: 2, lineTotalMinor: 10000 }],
  history: [
    { toStatus: 'CREATED', actorType: 'CUSTOMER', at: '2026-11-08T07:00:00Z' },
    { fromStatus: 'CREATED', toStatus: 'PENDING_PAYMENT', actorType: 'SYSTEM', reason: 'Stock held',
      at: '2026-11-08T07:00:01Z' },
  ],
};

describe('buildOrderView', () => {
  it('writes the history as "from → to", the first entry without a "from"', () => {
    const view = buildOrderView(AMINAS_ORDER);

    expect(view.history[0].change).toBe('Created');
    expect(view.history[1].change).toBe('Created → Waiting for payment');
    expect(view.history[1].by).toBe('System');
  });

  it('shows the pay-by time only while waiting for payment', () => {
    expect(buildOrderView(AMINAS_ORDER).payBy).toContain('10:15');

    const paid = { ...AMINAS_ORDER, status: 'PAID' };
    expect(buildOrderView(paid).payBy).toBe('');
  });
});

describe('OrderDetailPage', () => {
  let backend: HttpTestingController;
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideApiConfiguration(''),
        provideRouter([{ path: 'orders/:orderId', component: OrderDetailPage }]),
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  it('shows the customer, the lines and the history', async () => {
    await harness.navigateByUrl('/orders/o1', OrderDetailPage);
    backend.expectOne('/api/admin/orders/o1').flush(AMINAS_ORDER);
    await letPromisesRun();
    harness.detectChanges();

    const text = harness.routeNativeElement!.textContent || '';
    expect(text).toContain('Order ORD-0001');
    expect(text).toContain('Amina');
    expect(text).toContain('M-Pesa');
    expect(text).toContain('Sukuma bundle');
    expect(text).toContain('KES 100.00');
    expect(harness.routeNativeElement!.querySelectorAll('.timeline li').length).toBe(2);
  });

  it('says the order does not exist on a 404 (also for another shop\'s order)', async () => {
    await harness.navigateByUrl('/orders/other-shops-order', OrderDetailPage);
    backend.expectOne('/api/admin/orders/other-shops-order').flush(
      { status: 404, code: 'ORDER_NOT_FOUND', detail: 'Order not found' },
      { status: 404, statusText: 'Not Found' },
    );
    await letPromisesRun();
    harness.detectChanges();

    expect(harness.routeNativeElement!.textContent).toContain('This order does not exist.');
  });

  function letPromisesRun(): Promise<void> {
    return new Promise(function (resolve) {
      setTimeout(resolve, 0);
    });
  }
});
