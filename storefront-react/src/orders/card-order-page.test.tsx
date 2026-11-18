import { render, screen, waitFor } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import { routes } from '../routes'
import { fakeBackend } from '../test-backend'
import { FakeEventSource } from '../test-event-source'
import { saveOrderLink } from './order-links'

// Stripe.js can't run in tests (it loads from Stripe's servers), so the card form is replaced by a
// stand-in that shows what it was given. CardForm itself is tested in card-form.test.tsx.
vi.mock('./CardForm', () => ({
  CardForm: (props: { clientSecret: string; publishableKey: string }) => (
    <div data-testid="card-form">
      {props.clientSecret} {props.publishableKey}
    </div>
  ),
}))

const ORDER = '/api/store/mamamboga/orders/MM-000001'

function cardOrder(paymentStatus: string, withAction: boolean) {
  let paymentNextAction = null
  if (withAction) {
    paymentNextAction = { kind: 'CARD', clientSecret: 'pi_1_secret_x', publishableKey: 'pk_test_x' }
  }
  return {
    orderNumber: 'MM-000001',
    status: 'PENDING_PAYMENT',
    paymentMethod: 'CARD',
    totalMinor: 5000,
    currency: 'KES',
    expiresAt: new Date(Date.now() + 15 * 60 * 1000).toISOString(),
    lines: [{ productName: 'Sukuma bundle', unitPriceMinor: 5000, quantity: 1, lineTotalMinor: 5000 }],
    paymentStatus,
    paymentFailureReason: null,
    paymentNextAction,
  }
}

function openOrderPage(orderBody: object) {
  saveOrderLink('mamamboga', { orderNumber: 'MM-000001', accessToken: 'private-token' })
  const backend = fakeBackend()
    .answer('/api/store/mamamboga', 200, { name: 'Mama Mboga', slug: 'mamamboga', currency: 'KES' })
    .answer(ORDER, 200, orderBody)
    .answerPost(ORDER + '/stream-ticket', 200, { ticket: 't', streamUrl: '/api/store/order-stream?ticket=t' })
  backend.install()
  FakeEventSource.install()
  const router = createMemoryRouter(routes, { initialEntries: ['/s/mamamboga/orders/MM-000001'] })
  render(<RouterProvider router={router} />)
  return backend
}

describe('paying an order by card', () => {
  it('shows Stripe’s card form with what the server gave, instead of "check your phone"', async () => {
    openOrderPage(cardOrder('PENDING', true))

    expect(await screen.findByRole('heading', { name: 'Pay by card' })).toBeInTheDocument()
    expect(screen.getByTestId('card-form')).toHaveTextContent('pi_1_secret_x pk_test_x')
    expect(screen.queryByText(/Check your phone/)).not.toBeInTheDocument()
  })

  it('waits for the card form until the payment has reached Stripe, then loads the order again', async () => {
    const backend = openOrderPage(cardOrder('INITIATED', false))

    expect(await screen.findByText('Getting the card form ready...')).toBeInTheDocument()
    await waitFor(() => expect(FakeEventSource.opened).toHaveLength(1))
    // The server says the attempt reached Stripe: the page asks for the order again.
    backend.answer(ORDER, 200, cardOrder('PENDING', true))
    FakeEventSource.latest().send({
      orderNumber: 'MM-000001',
      status: 'PENDING_PAYMENT',
      paymentStatus: 'PENDING',
      message: 'Waiting for payment.',
      last: false,
    })

    expect(await screen.findByTestId('card-form')).toHaveTextContent('pi_1_secret_x')
    expect(backend.requests.filter((request) => request.path === ORDER).length).toBeGreaterThanOrEqual(2)
  })

  it('shows Paid when the server says so, and no card form', async () => {
    openOrderPage(cardOrder('PENDING', true))
    await waitFor(() => expect(FakeEventSource.opened).toHaveLength(1))

    FakeEventSource.latest().send({
      orderNumber: 'MM-000001',
      status: 'PAID',
      paymentStatus: 'SUCCEEDED',
      message: 'Payment received. Thank you!',
      last: true,
    })

    expect(await screen.findByRole('heading', { name: 'Paid. Thank you!' })).toBeInTheDocument()
    expect(screen.queryByTestId('card-form')).not.toBeInTheDocument()
  })
})
