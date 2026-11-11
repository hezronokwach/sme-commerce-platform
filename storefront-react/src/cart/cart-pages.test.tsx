import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { describe, expect, it } from 'vitest'
import { routes } from '../routes'
import { fakeBackend } from '../test-backend'

// The issue's Done-when, as a customer would do it: browse a shop and fill a cart.

function openPage(address: string) {
  const router = createMemoryRouter(routes, { initialEntries: [address] })
  return render(<RouterProvider router={router} />)
}

function shop(slug: string, name: string) {
  return { name, slug, currency: 'KES', primaryColor: '#0F766E', accentColor: '#F59E0B' }
}

const sukuma = { id: 'p1', name: 'Sukuma bundle', slug: 'sukuma-bundle', priceMinor: 5000, currency: 'KES' }
const tomatoes = { id: 'p2', name: 'Tomatoes', slug: 'tomatoes', priceMinor: 3000, currency: 'KES' }

function twoShops() {
  fakeBackend()
    .answer('/api/store/mamamboga', 200, shop('mamamboga', 'Mama Mboga'))
    .answer('/api/store/mamamboga/products/sukuma-bundle', 200, sukuma)
    .answer('/api/store/mamamboga/products/tomatoes', 200, tomatoes)
    .answer('/api/store/kamau', 200, shop('kamau', 'Kamau Hardware'))
    .install()
}

describe('filling a cart', () => {
  it('adds products from their pages and shows them, with the total, in the cart', async () => {
    twoShops()
    const page = openPage('/s/mamamboga/p/sukuma-bundle')

    const quantity = await screen.findByLabelText('Quantity')
    await userEvent.clear(quantity)
    await userEvent.type(quantity, '2')
    await userEvent.click(screen.getByRole('button', { name: 'Add to cart' }))

    expect(screen.getByRole('status')).toHaveTextContent(/^2 in your cart\./)
    expect(screen.getByRole('link', { name: 'Cart, 2 items' })).toBeInTheDocument()

    page.unmount()
    openPage('/s/mamamboga/p/tomatoes')
    await userEvent.click(await screen.findByRole('button', { name: 'Add to cart' }))
    await userEvent.click(screen.getByRole('link', { name: 'Cart, 3 items' }))

    expect(await screen.findByRole('heading', { name: 'Your cart' })).toBeInTheDocument()
    expect(screen.getByText('Sukuma bundle')).toBeInTheDocument()
    expect(screen.getByText('Tomatoes')).toBeInTheDocument()
    // 2 × 50.00 + 1 × 30.00
    expect(screen.getByText('KES 130.00')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Checkout (coming soon)' })).toBeDisabled()
  })

  it('keeps the cart after a reload, and another shop has its own empty cart', async () => {
    twoShops()
    const first = openPage('/s/mamamboga/p/sukuma-bundle')
    await userEvent.click(await screen.findByRole('button', { name: 'Add to cart' }))
    first.unmount()

    // "Reload": a brand-new page, which only has what was saved in the browser.
    const reloaded = openPage('/s/mamamboga/cart')
    expect(await screen.findByText('Sukuma bundle')).toBeInTheDocument()
    reloaded.unmount()

    openPage('/s/kamau/cart')
    expect(await screen.findByText('Your cart is empty.')).toBeInTheDocument()
  })

  it('changes quantities and removes lines in the cart', async () => {
    twoShops()
    const first = openPage('/s/mamamboga/p/sukuma-bundle')
    await userEvent.click(await screen.findByRole('button', { name: 'Add to cart' }))
    first.unmount()
    openPage('/s/mamamboga/cart')

    await userEvent.click(await screen.findByRole('button', { name: 'One more Sukuma bundle' }))
    expect(screen.getByLabelText('Quantity of Sukuma bundle')).toHaveTextContent('2')
    expect(screen.getByText('KES 100.00', { selector: 'strong' })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'One less Sukuma bundle' }))
    await userEvent.click(screen.getByRole('button', { name: 'One less Sukuma bundle' }))

    // Down to zero: the line is gone.
    expect(screen.getByText('Your cart is empty.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Cart, empty' })).toBeInTheDocument()
  })
})
