import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { describe, expect, it } from 'vitest'
import { routes } from '../routes'
import { fakeBackend } from '../test-backend'
import { FakeEventSource } from '../test-event-source'
import { saveOrderLink } from './order-links'

// The issue's Done-when, as the customer sees it: the payment status changes live on the order page.

const ORDER = '/api/store/mamamboga/orders/MM-000001'

function order(status: string, paymentStatus: string | null, failureReason: string | null) {
  return {
    orderNumber: 'MM-000001',
    status,
    paymentMethod: 'FAKE',
    totalMinor: 10000,
    currency: 'KES',
    expiresAt: new Date(Date.now() + 15 * 60 * 1000).toISOString(),
    phoneHint: '2547******70',
    lines: [{ productName: 'Sukuma bundle', unitPriceMinor: 5000, quantity: 2, lineTotalMinor: 10000 }],
    paymentStatus,
    paymentFailureReason: failureReason,
  }
}

function backendWith(orderBody: object) {
  const backend = fakeBackend()
    .answer('/api/store/mamamboga', 200, { name: 'Mama Mboga', slug: 'mamamboga', currency: 'KES' })
    .answer(ORDER, 200, orderBody)
    .answerPost(ORDER + '/stream-ticket', 200, { ticket: 't', streamUrl: '/api/store/order-stream?ticket=t' })
  backend.install()
  FakeEventSource.install()
  return backend
}

function openOrderPage() {
  const router = createMemoryRouter(routes, { initialEntries: ['/s/mamamboga/orders/MM-000001'] })
  render(<RouterProvider router={router} />)
}

describe('the live order page', () => {
  it('shows waiting for the PIN, then paid when the server says so', async () => {
    saveOrderLink('mamamboga', { orderNumber: 'MM-000001', accessToken: 'private-token' })
    const backend = backendWith(order('PENDING_PAYMENT', 'PENDING', null))
    openOrderPage()

    expect(await screen.findByRole('heading', { name: 'Check your phone' })).toBeInTheDocument()
    expect(screen.getByText(/We sent a payment prompt to 2547\*\*\*\*\*\*70/)).toBeInTheDocument()
    expect(screen.getByText(/left to pay/)).toBeInTheDocument()
    expect(screen.getByText('2 × Sukuma bundle')).toBeInTheDocument()
    // The order was read with the link in a header, not the address.
    const read = backend.requests.find((request) => request.path === ORDER)
    expect(read?.headers.get('X-Order-Token')).toBe('private-token')

    await waitFor(() => expect(FakeEventSource.opened).toHaveLength(1))
    FakeEventSource.latest().send({
      orderNumber: 'MM-000001',
      status: 'PAID',
      paymentStatus: 'SUCCEEDED',
      message: 'Payment received. Thank you!',
      last: true,
    })

    expect(await screen.findByRole('heading', { name: 'Paid. Thank you!' })).toBeInTheDocument()
    expect(screen.getByText('Payment received. Thank you!')).toBeInTheDocument()
  })

  it('after a failed payment, Try again starts a new one and the page waits again', async () => {
    saveOrderLink('mamamboga', { orderNumber: 'MM-000001', accessToken: 'private-token' })
    const backend = backendWith(order('PENDING_PAYMENT', 'PENDING', null))
    backend.answerPost(ORDER + '/payment-attempts', 202, null)
    openOrderPage()
    await waitFor(() => expect(FakeEventSource.opened).toHaveLength(1))

    FakeEventSource.latest().send({
      orderNumber: 'MM-000001',
      status: 'PENDING_PAYMENT',
      paymentStatus: 'FAILED',
      message: 'Payment failed: You cancelled the payment on your phone. You can try again.',
      last: false,
    })
    expect(await screen.findByRole('heading', { name: "Payment didn't go through" })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByRole('heading', { name: 'Check your phone' })).toBeInTheDocument()
    const retry = backend.requests.find((request) => request.path === ORDER + '/payment-attempts')
    expect(retry?.body).toEqual({ orderToken: 'private-token' })
    expect(screen.queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument()
  })

  it('shows "checking", never "failed", while the payment is UNKNOWN', async () => {
    saveOrderLink('mamamboga', { orderNumber: 'MM-000001', accessToken: 'private-token' })
    backendWith(order('PENDING_PAYMENT', 'UNKNOWN', null))
    openOrderPage()

    expect(await screen.findByRole('heading', { name: 'Checking your payment...' })).toBeInTheDocument()
    expect(screen.queryByText(/didn't go through/)).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument()
  })

  it('does not open a stream for an order that is already paid', async () => {
    saveOrderLink('mamamboga', { orderNumber: 'MM-000001', accessToken: 'private-token' })
    backendWith(order('PAID', 'SUCCEEDED', null))
    openOrderPage()

    expect(await screen.findByRole('heading', { name: 'Paid. Thank you!' })).toBeInTheDocument()
    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(FakeEventSource.opened).toHaveLength(0)
  })

  it('without the private link, explains where to open the order and asks for nothing', async () => {
    const backend = backendWith(order('PENDING_PAYMENT', 'PENDING', null))
    openOrderPage()

    expect(await screen.findByText(/Open this order on the phone or computer you ordered with/)).toBeInTheDocument()
    expect(backend.requests.filter((request) => request.path.startsWith(ORDER))).toHaveLength(0)
  })
})
