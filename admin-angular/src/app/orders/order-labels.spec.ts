import { actorLabel, formatDateTime, paymentMethodLabel, statusLabel } from './order-labels';

describe('order labels', () => {
  it('gives each status words people read', () => {
    expect(statusLabel('PENDING_PAYMENT')).toBe('Waiting for payment');
    expect(statusLabel('FULFILLED')).toBe('Handed over');
  });

  it('shows a status it does not know as it is', () => {
    expect(statusLabel('REFUNDED')).toBe('REFUNDED');
  });

  it('names the payment method and who made a change', () => {
    expect(paymentMethodLabel('MPESA')).toBe('M-Pesa');
    expect(actorLabel('CUSTOMER')).toBe('Customer');
  });
});

describe('formatDateTime', () => {
  it('shows a UTC time in Nairobi time (UTC+3)', () => {
    const text = formatDateTime('2026-11-08T07:05:00Z');

    expect(text).toContain('8 Nov 2026');
    expect(text).toContain('10:05');
  });

  it('shows text it cannot read as it is', () => {
    expect(formatDateTime('yesterday')).toBe('yesterday');
  });
});
