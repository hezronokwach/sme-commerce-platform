// The ways of paying a shop offers come from the shop's profile (its owner switches them in the admin).
// FAKE stands in for M-Pesa until M-Pesa is connected: the phone's last digit picks the outcome.

export type PaymentMethod = 'MPESA' | 'CARD' | 'FAKE'

// The shop's methods this page knows, in the order the shop lists them. Anything unknown is skipped.
// A shop always offers at least one (the backend refuses turning off the last), so the fallback is
// only for an older backend that doesn't list them yet.
export function offeredMethods(fromProfile: string[] | undefined): PaymentMethod[] {
  const methods: PaymentMethod[] = []
  if (fromProfile) {
    for (const method of fromProfile) {
      if (method === 'MPESA' || method === 'CARD' || method === 'FAKE') {
        methods.push(method)
      }
    }
  }
  if (methods.length === 0) {
    methods.push('FAKE')
  }
  return methods
}

export function payButtonText(method: PaymentMethod): string {
  if (method === 'CARD') {
    return 'Pay with card'
  }
  return 'Pay with M-Pesa'
}
