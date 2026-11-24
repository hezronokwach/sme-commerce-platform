import { useState } from 'react'
import type { FormEvent } from 'react'
import { api } from '../api/client'

// The shop's assistant: a small chat panel on every page of the shop. The conversation stays in this
// component only, so it is gone on a reload and is never stored in the browser.

const MAX_QUESTION_LENGTH = 500

type ChatMessage = {
  id: number
  from: 'customer' | 'assistant'
  text: string
  sources: string[]
}

export function AssistantPanel({ slug, shopName }: { slug: string; shopName: string }) {
  const [open, setOpen] = useState(false)
  const [question, setQuestion] = useState('')
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [asking, setAsking] = useState(false)
  const [nextId, setNextId] = useState(1)

  async function ask(event: FormEvent) {
    event.preventDefault()
    const text = question.trim()
    if (text === '' || asking) {
      return
    }

    const customerMessage: ChatMessage = { id: nextId, from: 'customer', text, sources: [] }
    const withQuestion = [...messages, customerMessage]
    setMessages(withQuestion)
    setNextId(nextId + 2)
    setQuestion('')
    setAsking(true)

    const reply = await getReply(slug, text)
    setMessages([...withQuestion, { id: nextId + 1, from: 'assistant', text: reply.text, sources: reply.sources }])
    setAsking(false)
  }

  if (!open) {
    return (
      <button type="button" className="assistant-open" onClick={() => setOpen(true)}>
        Ask the shop
      </button>
    )
  }

  return (
    <section className="assistant-panel" aria-label={`Assistant for ${shopName}`}>
      <div className="assistant-header">
        <h2>Ask {shopName}</h2>
        <button type="button" onClick={() => setOpen(false)} aria-label="Close the assistant">
          ×
        </button>
      </div>

      <div className="assistant-messages" aria-live="polite">
        {messages.length === 0 && (
          <p className="assistant-hint">Ask about products, delivery or returns. Please don't share personal details.</p>
        )}
        {messages.map((message) => (
          <div key={message.id} className={`assistant-message assistant-message-${message.from}`}>
            <p>{message.text}</p>
            {message.sources.length > 0 && <small>From: {message.sources.join(', ')}</small>}
          </div>
        ))}
        {asking && <p className="assistant-hint">Thinking...</p>}
      </div>

      <form className="assistant-form" onSubmit={ask}>
        <label htmlFor="assistant-question" className="visually-hidden">
          Your question
        </label>
        <input
          id="assistant-question"
          value={question}
          maxLength={MAX_QUESTION_LENGTH}
          onChange={(event) => setQuestion(event.target.value)}
          placeholder="Your question"
        />
        <button type="submit" className="primary-button" disabled={asking || question.trim() === ''}>
          Send
        </button>
      </form>
    </section>
  )
}

async function getReply(slug: string, question: string): Promise<{ text: string; sources: string[] }> {
  try {
    const { data, response } = await api.POST('/api/store/{shopSlug}/assistant/ask', {
      params: { path: { shopSlug: slug } },
      body: { question },
    })
    if (data) {
      return { text: data.answer ?? '', sources: data.sources ?? [] }
    }
    if (response.status === 429) {
      return { text: 'Please wait a minute before asking again.', sources: [] }
    }
  } catch {
    // No connection: the message below covers it.
  }
  return { text: "The assistant can't answer right now. Please try again in a moment.", sources: [] }
}
