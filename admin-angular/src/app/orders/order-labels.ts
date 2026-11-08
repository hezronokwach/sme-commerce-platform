/**
 * The words the screen shows for the backend's codes. The API sends enum names like PENDING_PAYMENT
 * (stable, good for code); people should read "Waiting for payment".
 *
 * An unknown code (a new status added to the backend before the admin learns it) is shown as it is,
 * rather than as an empty cell.
 */

/** The statuses the backend knows (the same list the generated adminOrderList accepts). */
export type OrderStatus = 'CREATED' | 'PENDING_PAYMENT' | 'PAID' | 'FULFILLED' | 'CANCELLED' | 'EXPIRED';

/** Every status, in the order an order moves through them. Used for the filter dropdown. */
export const ORDER_STATUSES: OrderStatus[] = ['CREATED', 'PENDING_PAYMENT', 'PAID', 'FULFILLED', 'CANCELLED', 'EXPIRED'];

export function statusLabel(status: string | undefined): string {
  if (status === undefined) {
    return '';
  }
  if (status === 'CREATED') {
    return 'Created';
  }
  if (status === 'PENDING_PAYMENT') {
    return 'Waiting for payment';
  }
  if (status === 'PAID') {
    return 'Paid';
  }
  if (status === 'FULFILLED') {
    return 'Handed over';
  }
  if (status === 'CANCELLED') {
    return 'Cancelled';
  }
  if (status === 'EXPIRED') {
    return 'Expired (not paid in time)';
  }
  return status;
}

export function paymentMethodLabel(method: string | undefined): string {
  if (method === undefined) {
    return '';
  }
  if (method === 'MPESA') {
    return 'M-Pesa';
  }
  if (method === 'CARD') {
    return 'Card';
  }
  if (method === 'FAKE') {
    return 'Test payment';
  }
  return method;
}

/** Who made a status change. */
export function actorLabel(actorType: string | undefined): string {
  if (actorType === undefined) {
    return '';
  }
  if (actorType === 'SYSTEM') {
    return 'System';
  }
  if (actorType === 'STAFF') {
    return 'Staff';
  }
  if (actorType === 'CUSTOMER') {
    return 'Customer';
  }
  return actorType;
}

/**
 * The backend sends every time in UTC ("2026-11-08T07:05:00Z", ADR on timestamps). The shops are in
 * Kenya, so the screen shows Nairobi time: "8 Nov 2026, 10:05".
 */
export function formatDateTime(isoText: string | undefined): string {
  if (isoText === undefined) {
    return '';
  }
  const date = new Date(isoText);
  if (isNaN(date.getTime())) {
    return isoText;
  }
  return date.toLocaleString('en-GB', {
    timeZone: 'Africa/Nairobi',
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}
