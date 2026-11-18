/**
 * The shop's live order feed (ADR 0012): something happened to one of the shop's orders.
 *
 *   1. ask for a one-time feed ticket (a normal API call, with the staff login);
 *   2. open new EventSource(streamUrl): one long response the server writes a line to on each change;
 *   3. hand each "order" message to the page;
 *   4. if the connection drops, close it, wait a little longer each time (1 s, 2 s, 5 s, 10 s, then
 *      every 30 s) and start again from 1 with a NEW ticket. A ticket works only once, so the browser's
 *      own reconnect (which would reuse it) is never left running.
 *
 * Plain TypeScript, not an Angular service: the page gives it a way to get a ticket, so tests can
 * drive it with a pretend server and a pretend EventSource.
 */

/** One message, as the server sends it (notification's ShopFeedMessage). */
export interface FeedMessage {
  type: string;
  orderId: string | null;
  orderNumber: string | null;
  status: string | null;
}

export type FeedState = 'connecting' | 'live' | 'reconnecting';

export interface OrderFeedOptions {
  /** Asks the backend for a ticket and returns the stream's address, e.g. /api/admin/order-feed?ticket=... */
  getStreamUrl: () => Promise<string>;
  /** Put in front of the stream's address: '' in development (the proxy), the backend's address when deployed. */
  rootUrl: string;
  onMessage: (message: FeedMessage) => void;
  onStateChange: (state: FeedState) => void;
  /** How long to wait before each new attempt, in milliseconds. Tests pass short ones. */
  retryDelays?: number[];
}

const DEFAULT_RETRY_DELAYS = [1000, 2000, 5000, 10000, 30000];

export class OrderFeed {
  private readonly options: OrderFeedOptions;
  private readonly retryDelays: number[];
  private stopped = false;
  private failedAttempts = 0;
  private source: EventSource | null = null;
  private retryTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(options: OrderFeedOptions) {
    this.options = options;
    if (options.retryDelays !== undefined) {
      this.retryDelays = options.retryDelays;
    } else {
      this.retryDelays = DEFAULT_RETRY_DELAYS;
    }
  }

  start(): void {
    this.options.onStateChange('connecting');
    this.connect();
  }

  /** Closes the stream and cancels any waiting retry. Called when the orders page closes. */
  stop(): void {
    this.stopped = true;
    if (this.source !== null) {
      this.source.close();
      this.source = null;
    }
    if (this.retryTimer !== null) {
      clearTimeout(this.retryTimer);
      this.retryTimer = null;
    }
  }

  private async connect(): Promise<void> {
    this.retryTimer = null;
    if (this.stopped) {
      return;
    }

    let streamUrl: string;
    try {
      streamUrl = await this.options.getStreamUrl();
    } catch {
      this.tryAgainLater(); // no connection, or the login ran out: try again later
      return;
    }
    if (this.stopped) {
      return;
    }

    const source = new EventSource(this.options.rootUrl + streamUrl);
    this.source = source;
    const feed = this;
    source.addEventListener('order', function (event: MessageEvent) {
      feed.handleMessage(event);
    });
    source.onerror = function () {
      feed.handleError();
    };
  }

  private handleMessage(event: MessageEvent): void {
    let message: FeedMessage;
    try {
      message = JSON.parse(event.data) as FeedMessage;
    } catch {
      return; // not a message we understand: ignore it
    }
    this.failedAttempts = 0;
    this.options.onStateChange('live');
    this.options.onMessage(message);
  }

  private handleError(): void {
    if (this.stopped) {
      return;
    }
    if (this.source !== null) {
      this.source.close();
      this.source = null;
    }
    this.tryAgainLater();
  }

  private tryAgainLater(): void {
    if (this.stopped) {
      return;
    }
    this.options.onStateChange('reconnecting');
    let index = this.failedAttempts;
    if (index >= this.retryDelays.length) {
      index = this.retryDelays.length - 1;
    }
    this.failedAttempts = this.failedAttempts + 1;
    const feed = this;
    this.retryTimer = setTimeout(function () {
      feed.connect();
    }, this.retryDelays[index]);
  }
}
