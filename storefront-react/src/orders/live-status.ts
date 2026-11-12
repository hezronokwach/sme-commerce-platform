import { api, backendAddress } from '../api/client'

// Watching one order live (Guide 6.3, ADR 0010):
//   1. ask for a one-time stream ticket, proving with the private order link that the order is ours;
//   2. open new EventSource(streamUrl): one long HTTP response the server writes a line to on each change;
//   3. on every "status" message, tell the page;
//   4. after a final status the server closes the stream, and we stop.
//
// If the connection drops, the browser's EventSource would reconnect by itself, but with the same
// ticket, and a ticket works only once. So we close it ourselves, wait a little longer each time
// (1 s, 2 s, 5 s, 10 s, then every 30 s), get a NEW ticket and open a new stream. The first message on
// every new stream is the order's current status, so nothing that happened while we were away is missed.

// One message, exactly as the server sends it (notification's StatusMessage).
export type StatusMessage = {
  orderNumber: string
  status: string
  paymentStatus: string | null
  message: string
  last: boolean
}

export type WatchOptions = {
  shopSlug: string
  orderNumber: string
  orderToken: string
  onStatus: (message: StatusMessage) => void
  // true while we are waiting to reconnect; false again once a stream is open
  onReconnecting: (reconnecting: boolean) => void
  // How long to wait before each new attempt, in milliseconds. Tests pass short ones.
  retryDelays?: number[]
}

const DEFAULT_RETRY_DELAYS = [1000, 2000, 5000, 10000, 30000]

// Starts watching and returns a function that stops it (closes the stream, cancels any waiting retry).
export function watchOrder(options: WatchOptions): () => void {
  let retryDelays = DEFAULT_RETRY_DELAYS
  if (options.retryDelays) {
    retryDelays = options.retryDelays
  }

  let stopped = false
  let failedAttempts = 0
  let source: EventSource | null = null
  let retryTimer: ReturnType<typeof setTimeout> | null = null

  function stop() {
    stopped = true
    if (source) {
      source.close()
      source = null
    }
    if (retryTimer) {
      clearTimeout(retryTimer)
      retryTimer = null
    }
  }

  function tryAgainLater() {
    if (stopped) {
      return
    }
    options.onReconnecting(true)
    let index = failedAttempts
    if (index >= retryDelays.length) {
      index = retryDelays.length - 1
    }
    failedAttempts = failedAttempts + 1
    retryTimer = setTimeout(connect, retryDelays[index])
  }

  function handleStatus(event: MessageEvent) {
    let message: StatusMessage
    try {
      message = JSON.parse(event.data) as StatusMessage
    } catch {
      return // not a message we understand: ignore it
    }
    failedAttempts = 0
    options.onReconnecting(false)
    options.onStatus(message)
    if (message.last) {
      // The server closes the stream now. Stop first, so that close isn't taken for a dropped connection.
      stop()
    }
  }

  function handleError() {
    if (stopped) {
      return
    }
    if (source) {
      source.close()
      source = null
    }
    tryAgainLater()
  }

  async function connect() {
    retryTimer = null
    if (stopped) {
      return
    }

    let streamUrl: string
    try {
      const { data } = await api.POST('/api/store/{slug}/orders/{orderNumber}/stream-ticket', {
        params: { path: { slug: options.shopSlug, orderNumber: options.orderNumber } },
        body: { orderToken: options.orderToken },
      })
      if (!data || !data.streamUrl) {
        tryAgainLater()
        return
      }
      streamUrl = data.streamUrl
    } catch {
      tryAgainLater() // no connection: try again later
      return
    }
    if (stopped) {
      return
    }

    source = new EventSource(backendAddress() + streamUrl)
    source.addEventListener('status', handleStatus)
    source.onerror = handleError
  }

  connect()
  return stop
}
