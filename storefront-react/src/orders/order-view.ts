// What the order page shows, decided from two plain facts the server sends: the order's status and
// the newest payment attempt's status. Never from the words in a message, so changing a sentence on
// the server can't change what the page does.
//
// The rule that matters most (CLAUDE.md, workflow W7): never say "failed" when the truth is "we don't
// know yet". "failed" appears only when the server says the payment FAILED. UNKNOWN, TIMED_OUT and
// anything this page doesn't recognise are shown as "checking".

export type OrderViewKind =
  | 'waiting' // the PIN prompt is on the customer's phone
  | 'checking' // we don't know yet how the payment ended
  | 'failed' // the payment didn't go through; the customer can try again
  | 'paid'
  | 'cancelled'
  | 'expired'

export function viewFor(orderStatus: string | null | undefined, paymentStatus: string | null | undefined): OrderViewKind {
  if (orderStatus === 'PAID' || orderStatus === 'FULFILLED') {
    return 'paid'
  }
  if (orderStatus === 'CANCELLED') {
    return 'cancelled'
  }
  if (orderStatus === 'EXPIRED') {
    return 'expired'
  }
  if (orderStatus === 'PENDING_PAYMENT' || orderStatus === 'CREATED') {
    if (paymentStatus === 'FAILED') {
      return 'failed'
    }
    if (paymentStatus === 'INITIATED' || paymentStatus === 'PENDING' || paymentStatus === null || paymentStatus === undefined) {
      return 'waiting'
    }
    // UNKNOWN, TIMED_OUT, or a status added later that this page doesn't know: "checking", never "failed".
    return 'checking'
  }
  return 'checking'
}

// After these, nothing more will happen to the payment: the page stops watching.
export function isFinal(kind: OrderViewKind): boolean {
  return kind === 'paid' || kind === 'cancelled' || kind === 'expired'
}

export function headingFor(kind: OrderViewKind): string {
  if (kind === 'waiting') {
    return 'Check your phone'
  }
  if (kind === 'checking') {
    return 'Checking your payment...'
  }
  if (kind === 'failed') {
    return "Payment didn't go through"
  }
  if (kind === 'paid') {
    return 'Paid. Thank you!'
  }
  if (kind === 'cancelled') {
    return 'Order cancelled'
  }
  return 'Expired'
}
