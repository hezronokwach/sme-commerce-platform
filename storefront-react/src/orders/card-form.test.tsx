import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { CardForm } from './CardForm'

// Stand-ins for Stripe's libraries: Stripe.js loads from Stripe's servers, which tests don't reach.
// confirmPayment is what the form calls when the customer presses Pay.
const confirmPayment = vi.fn()

vi.mock('@stripe/stripe-js', () => ({
  loadStripe: vi.fn(() => Promise.resolve({})),
}))

vi.mock('@stripe/react-stripe-js', () => ({
  Elements: (props: { children: ReactNode; options: { clientSecret: string } }) => (
    <div data-client-secret={props.options.clientSecret}>{props.children}</div>
  ),
  PaymentElement: () => <div>Stripe card fields</div>,
  useStripe: () => ({ confirmPayment }),
  useElements: () => ({}),
}))

describe('the card form', () => {
  beforeEach(() => {
    confirmPayment.mockReset()
  })

  it('confirms the payment with Stripe and stays on the page', async () => {
    confirmPayment.mockResolvedValue({})
    render(<CardForm clientSecret="pi_1_secret_x" publishableKey="pk_test_x" />)

    await userEvent.click(screen.getByRole('button', { name: 'Pay now' }))

    expect(confirmPayment).toHaveBeenCalledTimes(1)
    expect(confirmPayment.mock.calls[0][0].redirect).toBe('if_required')
    // The outcome comes from the server's live status, not from this form.
    expect(await screen.findByText('Card details sent. Confirming your payment...')).toBeInTheDocument()
  })

  it('shows Stripe’s own words when the card is refused straight away, and lets the customer retry', async () => {
    confirmPayment.mockResolvedValue({ error: { message: 'Your card was declined.' } })
    render(<CardForm clientSecret="pi_1_secret_x" publishableKey="pk_test_x" />)

    await userEvent.click(screen.getByRole('button', { name: 'Pay now' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Your card was declined.')
    expect(screen.getByRole('button', { name: 'Pay now' })).toBeEnabled()
  })
})
