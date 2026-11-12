/**
 * A pretend EventSource for tests (the test browser, jsdom, has none). Each stream the code opens is
 * kept in `opened`, and the test plays the server: send() an "order" message, drop() the connection.
 */
export class FakeEventSource {
  static opened: FakeEventSource[] = [];
  private static real: unknown;

  readonly url: string;
  closed = false;
  onerror: (() => void) | null = null;
  private readonly listeners: ((event: MessageEvent) => void)[] = [];

  constructor(url: string) {
    this.url = url;
    FakeEventSource.opened.push(this);
  }

  addEventListener(name: string, listener: (event: MessageEvent) => void): void {
    if (name === 'order') {
      this.listeners.push(listener);
    }
  }

  close(): void {
    this.closed = true;
  }

  /** The server writes one "order" message. */
  send(message: object): void {
    for (const listener of this.listeners) {
      listener(new MessageEvent('order', { data: JSON.stringify(message) }));
    }
  }

  /** The connection drops. */
  drop(): void {
    if (this.onerror !== null) {
      this.onerror();
    }
  }

  static latest(): FakeEventSource {
    return FakeEventSource.opened[FakeEventSource.opened.length - 1];
  }

  /** Puts the pretend one in place of the browser's EventSource. */
  static install(): void {
    FakeEventSource.opened = [];
    const holder = globalThis as unknown as { EventSource: unknown };
    FakeEventSource.real = holder.EventSource;
    holder.EventSource = FakeEventSource;
  }

  static uninstall(): void {
    const holder = globalThis as unknown as { EventSource: unknown };
    holder.EventSource = FakeEventSource.real;
  }
}
