// Which payment method the storefront asks for. M-Pesa isn't connected yet (a later issue), so the
// default is FAKE: the backend's fake provider picks the outcome from the phone's last digit.
// Once M-Pesa works, a deployed build sets VITE_PAYMENT_METHOD=MPESA; nothing else changes.

export type PaymentMethod = 'MPESA' | 'CARD' | 'FAKE'

export function configuredPaymentMethod(): PaymentMethod {
  const configured = import.meta.env.VITE_PAYMENT_METHOD
  if (configured === 'MPESA' || configured === 'CARD' || configured === 'FAKE') {
    return configured
  }
  return 'FAKE'
}
