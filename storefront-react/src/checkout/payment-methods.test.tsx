import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { describe, expect, it } from 'vitest'
import { saveCart } from '../cart/cart'
import { routes } from '../routes'
import { fakeBackend } from '../test-backend'
import { offeredMethods } from './payment-method'

const sukumaLine = { productId: 'p1', slug: 'sukuma-bundle', name: 'Sukuma bundle', priceMinor: 5000, currency: 'KES' }

function openCheckoutOfShopWith(paymentMethods: string[]) {
  saveCart('mamamboga', [{ ...sukumaLine, quantity: 1 }])
  const backend = fakeBackend()
    .answer('/api/store/mamamboga', 200, { name: 'Mama Mboga', slug: 'mamamboga', currency: 'KES', paymentMethods })
    .answerPost('/api/store/mamamboga/orders', 409, { code: 'INSUFFICIENT_STOCK', productId: 'p1', available: 0 })
  backend.install()
  const router = createMemoryRouter(routes, { initialEntries: ['/s/mamamboga/checkout'] })
  render(<RouterProvider router={router} />)
  return backend
}

describe('the ways of paying a shop offers', () => {
  it('keeps only methods this page knows, and falls back to M-Pesa for an older backend', () => {
    expect(offeredMethods(['FAKE', 'CARD', 'BITCOIN'])).toEqual(['FAKE', 'CARD'])
    expect(offeredMethods(undefined)).toEqual(['FAKE'])
    expect(offeredMethods([])).toEqual(['FAKE'])
  })

  it('shows one button per method the shop offers', async () => {
    openCheckoutOfShopWith(['FAKE', 'CARD'])

    expect(await screen.findByRole('button', { name: 'Pay with M-Pesa' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Pay with card' })).toBeInTheDocument()
  })

  it('a card-only shop offers only card, and sends CARD as the payment method', async () => {
    const backend = openCheckoutOfShopWith(['CARD'])

    await userEvent.type(await screen.findByLabelText('Your name'), 'Amina')
    await userEvent.type(screen.getByLabelText('Phone number'), '0712345670')
    expect(screen.queryByRole('button', { name: 'Pay with M-Pesa' })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Pay with card' }))

    await screen.findByRole('alert')
    const sent = backend.requests.find((request) => request.method === 'POST')
    expect(sent?.body).toMatchObject({ paymentMethod: 'CARD' })
    // No test-payments note: the fake provider isn't offered here.
    expect(screen.queryByText(/Test payments/)).not.toBeInTheDocument()
  })
})
