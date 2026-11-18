import { useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { Elements, PaymentElement, useElements, useStripe } from '@stripe/react-stripe-js'
import { loadStripe } from '@stripe/stripe-js'

// Stripe's own card form (the Payment Element), for one payment (ADR 0013). The card number goes from
// this form straight to Stripe and never passes through our server. Our server only gave us the
// payment's client secret (it lets this browser confirm this one payment) and Stripe's public key.
//
// After "Pay", Stripe tells our server by a signed webhook, and the live stream turns this page to
// "Paid" (or "didn't go through"), exactly as for M-Pesa. So this form doesn't decide the outcome; it
// only reports a problem Stripe shows straight away (a mistyped card number, a declined card).

export function CardForm({ clientSecret, publishableKey }: { clientSecret: string; publishableKey: string }) {
  // Load Stripe.js once per key, not on every render.
  const stripePromise = useMemo(() => loadStripe(publishableKey), [publishableKey])

  return (
    <Elements stripe={stripePromise} options={{ clientSecret }}>
      <PayWithCard />
    </Elements>
  )
}

function PayWithCard() {
  const stripe = useStripe()
  const elements = useElements()
  const [paying, setPaying] = useState(false)
  const [problem, setProblem] = useState('')
  const [sent, setSent] = useState(false)

  async function pay(event: FormEvent) {
    event.preventDefault()
    if (!stripe || !elements || paying) {
      return // Stripe.js is still loading, or a payment is on its way
    }
    setPaying(true)
    setProblem('')

    // redirect 'if_required': stay on this page for cards; only payment methods that need another page
    // (some bank checks) would leave it.
    const result = await stripe.confirmPayment({
      elements,
      confirmParams: { return_url: window.location.href },
      redirect: 'if_required',
    })
    if (result.error) {
      let message = 'The card payment did not go through. Check the details and try again.'
      if (result.error.message) {
        message = result.error.message
      }
      setProblem(message)
    } else {
      setSent(true) // the live status will say "Paid" as soon as Stripe tells our server
    }
    setPaying(false)
  }

  if (sent) {
    return <p aria-busy="true">Card details sent. Confirming your payment...</p>
  }

  let buttonText = 'Pay now'
  if (paying) {
    buttonText = 'Paying...'
  }
  return (
    <form className="card-form" onSubmit={pay}>
      <PaymentElement />
      {problem && (
        <p role="alert" className="form-error">
          {problem}
        </p>
      )}
      <button type="submit" className="primary-button" disabled={!stripe || paying}>
        {buttonText}
      </button>
    </form>
  )
}
