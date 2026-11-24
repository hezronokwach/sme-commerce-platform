import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { describe, expect, it } from 'vitest'
import { routes } from '../routes'
import { fakeBackend } from '../test-backend'

const shop = { name: 'Mama Mboga', slug: 'mamamboga', currency: 'KES' }
const askPath = '/api/store/mamamboga/assistant/ask'

function openShop() {
  const router = createMemoryRouter(routes, { initialEntries: ['/s/mamamboga'] })
  render(<RouterProvider router={router} />)
}

async function askQuestion(question: string) {
  const user = userEvent.setup()
  await user.click(await screen.findByRole('button', { name: 'Ask the shop' }))
  await user.type(screen.getByLabelText('Your question'), question)
  await user.click(screen.getByRole('button', { name: 'Send' }))
}

describe('assistant panel', () => {
  it('sends the question to this shop and shows the answer with its sources', async () => {
    const backend = fakeBackend()
      .answer('/api/store/mamamboga', 200, shop)
      .answerPost(askPath, 200, { answer: 'We deliver from 9am.', sources: ['Delivery policy'] })
    backend.install()
    openShop()

    await askQuestion('Do you deliver on Sundays?')

    expect(await screen.findByText('We deliver from 9am.')).toBeInTheDocument()
    expect(screen.getByText('From: Delivery policy')).toBeInTheDocument()
    expect(screen.getByText('Do you deliver on Sundays?')).toBeInTheDocument()
    const sent = backend.requests.find((request) => request.path === askPath)
    expect(sent?.body).toEqual({ question: 'Do you deliver on Sundays?' })
  })

  it('asks the customer to wait when there were too many questions', async () => {
    fakeBackend()
      .answer('/api/store/mamamboga', 200, shop)
      .answerPost(askPath, 429, { code: 'TOO_MANY_QUESTIONS' })
      .install()
    openShop()

    await askQuestion('Hello?')

    expect(await screen.findByText('Please wait a minute before asking again.')).toBeInTheDocument()
  })

  it('says so plainly when the assistant fails', async () => {
    fakeBackend()
      .answer('/api/store/mamamboga', 200, shop)
      .answerPost(askPath, 500, { code: 'INTERNAL_ERROR' })
      .install()
    openShop()

    await askQuestion('Hello?')

    expect(
      await screen.findByText("The assistant can't answer right now. Please try again in a moment."),
    ).toBeInTheDocument()
  })
})
