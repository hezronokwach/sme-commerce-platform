import { waitFor } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { fakeBackend } from '../test-backend'
import { FakeEventSource } from '../test-event-source'
import { watchOrder } from './live-status'
import type { StatusMessage } from './live-status'

const TICKET_PATH = '/api/store/mamamboga/orders/MM-000001/stream-ticket'

// Each ticket request gets a new ticket, like the real server.
function backendHandingOutTickets() {
  const backend = fakeBackend()
  backend.answerPost(TICKET_PATH, 200, { ticket: 't', streamUrl: '/api/store/order-stream?ticket=t' })
  backend.install()
  return backend
}

function startWatching(received: StatusMessage[], reconnecting: boolean[]) {
  return watchOrder({
    shopSlug: 'mamamboga',
    orderNumber: 'MM-000001',
    orderToken: 'private-token',
    onStatus: (message) => received.push(message),
    onReconnecting: (value) => reconnecting.push(value),
    retryDelays: [10, 20],
  })
}

const waiting = { orderNumber: 'MM-000001', status: 'PENDING_PAYMENT', paymentStatus: 'PENDING', message: 'Waiting', last: false }
const paid = { orderNumber: 'MM-000001', status: 'PAID', paymentStatus: 'SUCCEEDED', message: 'Paid', last: true }

describe('watching an order live', () => {
  it('asks for a ticket with the order link in the body, then opens the stream', async () => {
    const backend = backendHandingOutTickets()
    FakeEventSource.install()
    const stop = startWatching([], [])

    await waitFor(() => expect(FakeEventSource.opened).toHaveLength(1))
    expect(backend.requests[0].body).toEqual({ orderToken: 'private-token' })
    expect(FakeEventSource.latest().url).toContain('/api/store/order-stream?ticket=t')
    // The private order link is never in a web address.
    expect(FakeEventSource.latest().url).not.toContain('private-token')
    stop()
  })

  it('passes each status on, and stops after the last one', async () => {
    backendHandingOutTickets()
    FakeEventSource.install()
    const received: StatusMessage[] = []
    startWatching(received, [])
    await waitFor(() => expect(FakeEventSource.opened).toHaveLength(1))

    FakeEventSource.latest().send(waiting)
    FakeEventSource.latest().send(paid)

    expect(received.map((message) => message.status)).toEqual(['PENDING_PAYMENT', 'PAID'])
    expect(FakeEventSource.latest().closed).toBe(true)
    // The server closing the stream after "last" is not a dropped connection: no reconnect.
    FakeEventSource.latest().drop()
    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(FakeEventSource.opened).toHaveLength(1)
  })

  it('after a dropped connection, gets a NEW ticket and opens a new stream', async () => {
    const backend = backendHandingOutTickets()
    FakeEventSource.install()
    const reconnecting: boolean[] = []
    const stop = startWatching([], reconnecting)
    await waitFor(() => expect(FakeEventSource.opened).toHaveLength(1))

    FakeEventSource.latest().drop()

    expect(FakeEventSource.opened[0].closed).toBe(true) // not left to retry with the used ticket
    expect(reconnecting).toEqual([true])
    await waitFor(() => expect(FakeEventSource.opened).toHaveLength(2))
    expect(backend.requests.filter((request) => request.path === TICKET_PATH)).toHaveLength(2)

    FakeEventSource.latest().send(waiting)
    expect(reconnecting).toEqual([true, false])
    stop()
  })

  it('keeps trying while the ticket request fails, and stop() ends it', async () => {
    const backend = fakeBackend()
    backend.answerPost(TICKET_PATH, 503, { code: 'UNAVAILABLE' })
    backend.install()
    FakeEventSource.install()
    const stop = startWatching([], [])

    await waitFor(() =>
      expect(backend.requests.filter((request) => request.path === TICKET_PATH).length).toBeGreaterThanOrEqual(3),
    )
    stop()
    const asked = backend.requests.length
    await new Promise((resolve) => setTimeout(resolve, 60))
    expect(backend.requests.length).toBe(asked)
    expect(FakeEventSource.opened).toHaveLength(0)
  })
})
