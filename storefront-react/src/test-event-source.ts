import { vi } from 'vitest'

// A pretend EventSource for tests (jsdom has none). Each stream the page opens is kept in `opened`,
// and the test plays the server: send() a status message, drop() the connection.

export class FakeEventSource {
  static opened: FakeEventSource[] = []

  readonly url: string
  closed = false
  onerror: (() => void) | null = null
  private readonly listeners = new Map<string, ((event: MessageEvent) => void)[]>()

  constructor(url: string) {
    this.url = url
    FakeEventSource.opened.push(this)
  }

  addEventListener(name: string, listener: (event: MessageEvent) => void) {
    let list = this.listeners.get(name)
    if (!list) {
      list = []
      this.listeners.set(name, list)
    }
    list.push(listener)
  }

  close() {
    this.closed = true
  }

  // The server writes one "status" message.
  send(message: object) {
    const list = this.listeners.get('status') ?? []
    for (const listener of list) {
      listener(new MessageEvent('status', { data: JSON.stringify(message) }))
    }
  }

  // The connection drops.
  drop() {
    if (this.onerror) {
      this.onerror()
    }
  }

  static install() {
    FakeEventSource.opened = []
    vi.stubGlobal('EventSource', FakeEventSource)
  }

  static latest(): FakeEventSource {
    return FakeEventSource.opened[FakeEventSource.opened.length - 1]
  }
}
