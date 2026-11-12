import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideApiConfiguration } from '../api/api-configuration';
import { FakeEventSource } from './fake-event-source';
import { OrdersPage, buildOrderRows, countPages } from './orders-page';

describe('buildOrderRows', () => {
  it('turns codes and minor units into text people read', () => {
    const rows = buildOrderRows({
      items: [{ id: 'o1', orderNumber: 'ORD-0001', customerName: 'Amina', status: 'PENDING_PAYMENT',
        totalMinor: 15000, currency: 'KES', createdAt: '2026-11-08T07:05:00Z' }],
      page: 0, size: 20, totalItems: 1,
    });

    expect(rows[0].statusLabel).toBe('Waiting for payment');
    expect(rows[0].total).toBe('KES 150.00');
    expect(rows[0].placedAt).toContain('10:05');
  });
});

describe('countPages', () => {
  it('rounds up, and an empty list is still one page', () => {
    expect(countPages(41, 20)).toBe(3);
    expect(countPages(40, 20)).toBe(2);
    expect(countPages(0, 20)).toBe(1);
  });
});

describe('OrdersPage', () => {
  let backend: HttpTestingController;
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideApiConfiguration(''),
        provideRouter([{ path: 'orders', component: OrdersPage }]),
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  it('asks for the first page of every status', async () => {
    await harness.navigateByUrl('/orders', OrdersPage);

    const request = backend.expectOne(function (r) {
      return r.url === '/api/admin/orders';
    });
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('20');
    expect(request.request.params.has('status')).toBe(false);
  });

  it('reads the filter and page from the address, so Back returns to the same list', async () => {
    await harness.navigateByUrl('/orders?status=PAID&page=2', OrdersPage);

    const request = backend.expectOne(function (r) {
      return r.url === '/api/admin/orders';
    });
    expect(request.request.params.get('status')).toBe('PAID');
    // The address counts from 1 for people, the API from 0.
    expect(request.request.params.get('page')).toBe('1');
  });

  it('loads again when only the address query changes, like pressing Back', async () => {
    await harness.navigateByUrl('/orders?status=PAID', OrdersPage);
    backend.expectOne(function (r) {
      return r.url === '/api/admin/orders';
    }).flush({ items: [], page: 0, size: 20, totalItems: 0 });
    await letPromisesRun();

    // Same page, only "?status=PAID" goes away: Angular keeps the component.
    await harness.navigateByUrl('/orders');

    const again = backend.expectOne(function (r) {
      return r.url === '/api/admin/orders';
    });
    expect(again.request.params.has('status')).toBe(false);
  });

  it('shows the rows and moves to the next page', async () => {
    await harness.navigateByUrl('/orders', OrdersPage);
    backend.expectOne(function (r) {
      return r.url === '/api/admin/orders';
    }).flush({ items: [{ id: 'o1', orderNumber: 'ORD-0001', customerName: 'Amina', status: 'PAID' }],
      page: 0, size: 20, totalItems: 25 });
    await letPromisesRun();
    harness.detectChanges();

    const page = harness.routeNativeElement!;
    expect(page.querySelectorAll('tbody tr').length).toBe(1);
    expect(page.textContent).toContain('Page 1 of 2');

    const buttons = page.querySelectorAll('.pager button');
    (buttons[1] as HTMLButtonElement).click();
    await letPromisesRun();

    const next = backend.expectOne(function (r) {
      return r.url === '/api/admin/orders';
    });
    expect(next.request.params.get('page')).toBe('1');
    expect(TestBed.inject(Router).url).toBe('/orders?page=2');
  });

  describe('live', () => {
    beforeEach(() => {
      FakeEventSource.install();
    });

    afterEach(() => {
      FakeEventSource.uninstall();
    });

    it('opens the shop feed, and a new order reloads the list and is highlighted', async () => {
      await harness.navigateByUrl('/orders', OrdersPage);
      backend.expectOne(function (r) {
        return r.url === '/api/admin/orders';
      }).flush({ items: [], page: 0, size: 20, totalItems: 0 });
      backend.expectOne(function (r) {
        return r.url === '/api/admin/order-feed/ticket' && r.method === 'POST';
      }).flush({ ticket: 't', streamUrl: '/api/admin/order-feed?ticket=t', expiresInSeconds: 60 });
      await letPromisesRun();

      expect(FakeEventSource.latest().url).toBe('/api/admin/order-feed?ticket=t');
      FakeEventSource.latest().send({ type: 'connected', orderId: null, orderNumber: null, status: null });
      harness.detectChanges();
      expect(harness.routeNativeElement!.textContent).toContain('Live');

      FakeEventSource.latest().send({ type: 'order-placed', orderId: 'o9', orderNumber: 'ORD-0009', status: 'PENDING_PAYMENT' });

      // The list is loaded again, by itself, and shows the new order highlighted.
      backend.expectOne(function (r) {
        return r.url === '/api/admin/orders';
      }).flush({ items: [{ id: 'o9', orderNumber: 'ORD-0009', customerName: 'Amina', status: 'PENDING_PAYMENT' }],
        page: 0, size: 20, totalItems: 1 });
      await letPromisesRun();
      harness.detectChanges();

      const row = harness.routeNativeElement!.querySelector('tbody tr')!;
      expect(row.textContent).toContain('ORD-0009');
      expect(row.classList.contains('new-order')).toBe(true);
    });

    it('stops the feed when the page closes', async () => {
      await harness.navigateByUrl('/orders', OrdersPage);
      backend.expectOne(function (r) {
        return r.url === '/api/admin/order-feed/ticket';
      }).flush({ ticket: 't', streamUrl: '/api/admin/order-feed?ticket=t', expiresInSeconds: 60 });
      await letPromisesRun();
      const stream = FakeEventSource.latest();

      TestBed.resetTestingModule(); // closes the page, as leaving it would

      expect(stream.closed).toBe(true);
    });
  });

  function letPromisesRun(): Promise<void> {
    return new Promise(function (resolve) {
      setTimeout(resolve, 0);
    });
  }
});
