import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { describe, expect, it } from 'vitest'
import { saveCart } from '../cart/cart'
import { findOrderToken } from '../orders/order-links'
import { routes } from '../routes'
import { fakeBackend } from '../test-backend'
import { FakeEventSource } from '../test-event-source'

const mamaMboga = { name: 'Mama Mboga', slug: 'mamamboga', currency: 'KES' }
const sukumaLine = { productId: 'p1', slug: 'sukuma-bundle', name: 'Sukuma bundle', priceMinor: 5000, currency: 'KES' }

function openCheckout() {
  const router = createMemoryRouter(routes, { initialEntries: ['/s/mamamboga/checkout'] })
  render(<RouterProvider router={router} />)
  return router
}

function backendWithShop() {
  return fakeBackend().answer('/api/store/mamamboga', 200, mamaMboga)
}

async function fillIn(name: string, phone: string) {
  await userEvent.type(await screen.findByLabelText('Your name'), name)
  await userEvent.type(screen.getByLabelText('M-Pesa phone number'), phone)
  await userEvent.click(screen.getByRole('button', { name: 'Pay with M-Pesa' }))
}

describe('checkout', () => {
  it('places the order with ids and quantities only, keeps the link, empties the cart and opens the order', async () => {
    saveCart('mamamboga', [{ ...sukumaLine, quantity: 2 }])
    const backend = backendWithShop().answerPost('/api/store/mamamboga/orders', 201, {
      orderId: 'o1',
      orderNumber: 'MM-000001',
      accessToken: 'private-token',
      status: 'PENDING_PAYMENT',
      totalMinor: 10000,
      currency: 'KES',
    })
    backend.answer('/api/store/mamamboga/orders/MM-000001', 200, {
      orderNumber: 'MM-000001',
      status: 'PENDING_PAYMENT',
      totalMinor: 10000,
      currency: 'KES',
      lines: [],
      paymentStatus: 'INITIATED',
    })
    backend.install()
    FakeEventSource.install()
    const router = openCheckout()

    await fillIn('Amina', '0712 345 670')

    // The order page opens, and finds the saved link.
    expect(await screen.findByRole('heading', { name: 'Check your phone' })).toBeInTheDocument()
    const sent = backend.requests.find((request) => request.method === 'POST')
    expect(sent?.body).toEqual({
      customerName: 'Amina',
      customerPhone: '254712345670',
      paymentMethod: 'FAKE',
      // No price: the server prices the order.
      lines: [{ productId: 'p1', quantity: 2 }],
    })
    expect(findOrderToken('mamamboga', 'MM-000001')).toBe('private-token')
    expect(window.localStorage.getItem('cart:mamamboga')).toBeNull()
    // The token never goes in the address.
    expect(router.state.location.pathname).toBe('/s/mamamboga/orders/MM-000001')
    expect(router.state.location.search).toBe('')
  })

  it('points out a wrong phone before sending anything', async () => {
    saveCart('mamamboga', [{ ...sukumaLine, quantity: 1 }])
    const backend = backendWithShop()
    backend.install()
    openCheckout()

    await fillIn('Amina', '0712 34')

    expect(screen.getByText('Enter a Kenyan mobile number, for example 0712 345 678.')).toBeInTheDocument()
    expect(backend.requests.filter((request) => request.method === 'POST')).toHaveLength(0)
  })

  it('names the product when there is not enough stock, and keeps the cart', async () => {
    saveCart('mamamboga', [{ ...sukumaLine, quantity: 5 }])
    backendWithShop()
      .answerPost('/api/store/mamamboga/orders', 409, {
        code: 'INSUFFICIENT_STOCK',
        detail: 'Only 2 available, but 5 were asked for.',
        productId: 'p1',
        available: 2,
      })
      .install()
    openCheckout()

    await fillIn('Amina', '0712345670')

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Only 2 left of Sukuma bundle. Lower the quantity in your cart to continue.',
    )
    expect(window.localStorage.getItem('cart:mamamboga')).not.toBeNull()
    expect(screen.getByRole('button', { name: 'Pay with M-Pesa' })).toBeEnabled()
  })

  it('says so when a product is no longer for sale', async () => {
    saveCart('mamamboga', [{ ...sukumaLine, quantity: 1 }])
    backendWithShop()
      .answerPost('/api/store/mamamboga/orders', 409, { code: 'PRODUCT_UNAVAILABLE', productId: 'p1' })
      .install()
    openCheckout()

    await fillIn('Amina', '0712345670')

    expect(await screen.findByRole('alert')).toHaveTextContent('Sukuma bundle is no longer for sale.')
  })

  it('sends nothing for an empty cart', async () => {
    backendWithShop().install()
    openCheckout()

    expect(await screen.findByText('Your cart is empty.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Pay with M-Pesa' })).not.toBeInTheDocument()
  })
})
