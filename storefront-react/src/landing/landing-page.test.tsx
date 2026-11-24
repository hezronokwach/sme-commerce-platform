import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { describe, expect, it } from 'vitest'
import { routes } from '../routes'
import { fakeBackend } from '../test-backend'
import { LOCAL_ADMIN } from './admin-address'

function openPage(address: string) {
  const router = createMemoryRouter(routes, { initialEntries: [address] })
  render(<RouterProvider router={router} />)
  return router
}

describe('the landing page', () => {
  it('says what the platform is and sends shop owners to the admin to sign up', () => {
    fakeBackend().install()

    openPage('/')

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Your shop online. Paid by M-Pesa.')
    // Tests run as development, so the links point at the local admin.
    const signUpLinks = screen.getAllByRole('link', { name: 'Open your shop' })
    expect(signUpLinks.length).toBeGreaterThan(0)
    for (const link of signUpLinks) {
      expect(link).toHaveAttribute('href', LOCAL_ADMIN + '/signup')
    }
    expect(screen.getByRole('link', { name: 'Log in' })).toHaveAttribute('href', LOCAL_ADMIN + '/login')
  })

  it("opens a shop from its web name, cleaned up the way shop web names are written", async () => {
    fakeBackend().install()
    const router = openPage('/')

    await userEvent.type(screen.getByLabelText("Shop's web name"), '  Kamau-Hardware ')
    await userEvent.click(screen.getByRole('button', { name: 'Go' }))

    expect(router.state.location.pathname).toBe('/s/kamau-hardware')
  })

  it('does nothing when the web name is empty', async () => {
    fakeBackend().install()
    const router = openPage('/')

    await userEvent.click(screen.getByRole('button', { name: 'Go' }))

    expect(router.state.location.pathname).toBe('/')
  })
})
