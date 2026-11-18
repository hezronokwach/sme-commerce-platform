import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideApiConfiguration } from '../api/api-configuration';
import { PaymentMethodsSection, buildPaymentMethodRows } from './payment-methods-section';

const fakeOnly = [
  { provider: 'FAKE', displayName: 'M-Pesa (test)', enabled: true, available: true },
  { provider: 'CARD', displayName: 'Card', enabled: false, available: true },
  { provider: 'MPESA', displayName: 'M-Pesa', enabled: false, available: false },
];

describe('buildPaymentMethodRows', () => {
  it('reads missing values as off and not available', () => {
    const rows = buildPaymentMethodRows([{ provider: 'CARD', displayName: 'Card' }]);

    expect(rows[0].enabled).toBe(false);
    expect(rows[0].available).toBe(false);
  });
});

describe('PaymentMethodsSection', () => {
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [PaymentMethodsSection],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideApiConfiguration('')],
    });
    backend = TestBed.inject(HttpTestingController);
  });

  async function shown() {
    const fixture = TestBed.createComponent(PaymentMethodsSection);
    fixture.detectChanges();
    backend.expectOne('/api/admin/settings/payment-methods').flush(fakeOnly);
    await letPromisesRun();
    fixture.detectChanges();
    return fixture;
  }

  it('shows each method with its switch, and M-Pesa as not available yet', async () => {
    const fixture = await shown();
    const boxes = fixture.nativeElement.querySelectorAll('input[type=checkbox]') as NodeListOf<HTMLInputElement>;

    expect(boxes.length).toBe(3);
    expect(boxes[0].checked).toBe(true);
    expect(boxes[1].checked).toBe(false);
    expect(boxes[2].disabled).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Not available yet');
  });

  it('turning card on asks the backend and shows the list it answers with', async () => {
    const fixture = await shown();
    const card = fixture.nativeElement.querySelectorAll('input[type=checkbox]')[1] as HTMLInputElement;

    card.click();
    const request = backend.expectOne('/api/admin/settings/payment-methods/CARD');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ enabled: true });
    request.flush([fakeOnly[0], { ...fakeOnly[1], enabled: true }, fakeOnly[2]]);
    await letPromisesRun();
    fixture.detectChanges();

    const boxes = fixture.nativeElement.querySelectorAll('input[type=checkbox]') as NodeListOf<HTMLInputElement>;
    expect(boxes[1].checked).toBe(true);
  });

  it('when the backend refuses (the last method), says why and puts the switch back', async () => {
    const fixture = await shown();
    const fake = fixture.nativeElement.querySelectorAll('input[type=checkbox]')[0] as HTMLInputElement;

    fake.click();
    backend.expectOne('/api/admin/settings/payment-methods/FAKE').flush(
      { code: 'LAST_PAYMENT_METHOD', detail: 'Turn another payment method on first. Customers need at least one way to pay.' },
      { status: 409, statusText: 'Conflict' });
    await letPromisesRun();
    fixture.detectChanges();

    expect(fake.checked).toBe(true);
    expect(fixture.nativeElement.querySelector('[role=alert]').textContent).toContain('at least one way to pay');
  });

  function letPromisesRun(): Promise<void> {
    return new Promise(function (resolve) {
      setTimeout(resolve, 0);
    });
  }
});
