import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { describe, expect, it } from 'vitest'
import { routes } from '../routes'
import { fakeBackend } from '../test-backend'

// The storefront's real routes, opened at an address, talking to a pretend backend.
function openPage(address: string) {
  const router = createMemoryRouter(routes, { initialEntries: [address] })
  render(<RouterProvider router={router} />)
}

const mamaMboga = {
  name: 'Mama Mboga',
  slug: 'mamamboga',
  currency: 'KES',
  primaryColor: '#0F766E',
  accentColor: '#F59E0B',
  tagline: 'Fresh every morning',
}

const sukuma = {
  id: 'p1',
  categoryId: 'c1',
  name: 'Sukuma bundle',
  slug: 'sukuma-bundle',
  description: 'Freshly cut',
  priceMinor: 5000,
  currency: 'KES',
}

const tomatoes = { id: 'p2', name: 'Tomatoes', slug: 'tomatoes', priceMinor: 3000, currency: 'KES' }

describe('shop pages', () => {
  it('says "Shop not found" for an unknown shop and never asks for its products', async () => {
    const backend = fakeBackend().answer('/api/store/nobody', 404, { code: 'SHOP_NOT_FOUND' })
    backend.install()

    openPage('/s/nobody')

    expect(await screen.findByRole('heading', { name: 'Shop not found' })).toBeInTheDocument()
    expect(backend.askedFor).toEqual(['/api/store/nobody'])
  })

  it("shows the shop's name and colours, then its products with prices", async () => {
    fakeBackend()
      .answer('/api/store/mamamboga', 200, mamaMboga)
      .answer('/api/store/mamamboga/categories', 200, [])
      .answer('/api/store/mamamboga/products', 200, [sukuma, tomatoes])
      .install()

    openPage('/s/mamamboga')

    expect(await screen.findByText('Sukuma bundle')).toBeInTheDocument()
    expect(screen.getByText('Tomatoes')).toBeInTheDocument()
    expect(screen.getByText('KES 50.00')).toBeInTheDocument()
    expect(screen.getByText('Mama Mboga')).toBeInTheDocument()

    const shop = document.querySelector('.shop') as HTMLElement
    expect(shop.style.getPropertyValue('--primary')).toBe('#0F766E')
  })

  it('narrows the list when a category is chosen', async () => {
    fakeBackend()
      .answer('/api/store/mamamboga', 200, mamaMboga)
      .answer('/api/store/mamamboga/categories', 200, [{ id: 'c1', name: 'Vegetables', slug: 'vegetables', sortOrder: 0 }])
      .answer('/api/store/mamamboga/products', 200, [sukuma, tomatoes])
      .answer('/api/store/mamamboga/products?categoryId=c1', 200, [sukuma])
      .install()
    openPage('/s/mamamboga')
    expect(await screen.findByText('Tomatoes')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Vegetables' }))

    expect(await screen.findByText('Sukuma bundle')).toBeInTheDocument()
    expect(screen.queryByText('Tomatoes')).not.toBeInTheDocument()
  })

  it('opens a product page from the list', async () => {
    fakeBackend()
      .answer('/api/store/mamamboga', 200, mamaMboga)
      .answer('/api/store/mamamboga/categories', 200, [])
      .answer('/api/store/mamamboga/products', 200, [sukuma])
      .answer('/api/store/mamamboga/products/sukuma-bundle', 200, sukuma)
      .install()
    openPage('/s/mamamboga')

    await userEvent.click(await screen.findByRole('link', { name: /Sukuma bundle/ }))

    expect(await screen.findByRole('heading', { name: 'Sukuma bundle' })).toBeInTheDocument()
    expect(screen.getByText('Freshly cut')).toBeInTheDocument()
  })

  it('says "Product not found" for a product that is not on sale', async () => {
    fakeBackend()
      .answer('/api/store/mamamboga', 200, mamaMboga)
      .answer('/api/store/mamamboga/products/secret', 404, { code: 'PRODUCT_NOT_FOUND' })
      .install()

    openPage('/s/mamamboga/p/secret')

    expect(await screen.findByRole('heading', { name: 'Product not found' })).toBeInTheDocument()
  })

  it('says "Something went wrong" when the backend is down', async () => {
    fakeBackend().answer('/api/store/mamamboga', 503, { code: 'UNAVAILABLE' }).install()

    openPage('/s/mamamboga')

    expect(await screen.findByRole('heading', { name: 'Something went wrong' })).toBeInTheDocument()
  })

  it('shows "Page not found" for an address outside any shop', async () => {
    fakeBackend().install()

    openPage('/')

    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument()
  })
})
