import { FakeEventSource } from './fake-event-source';
import { FeedMessage, FeedState, OrderFeed } from './order-feed';

describe('OrderFeed', () => {
  let ticketsAsked: number;
  let messages: FeedMessage[];
  let states: FeedState[];

  beforeEach(() => {
    FakeEventSource.install();
    ticketsAsked = 0;
    messages = [];
    states = [];
  });

  afterEach(() => {
    FakeEventSource.uninstall();
  });

  function newFeed(getStreamUrl: () => Promise<string>): OrderFeed {
    return new OrderFeed({
      getStreamUrl: getStreamUrl,
      rootUrl: '',
      onMessage: function (message) {
        messages.push(message);
      },
      onStateChange: function (state) {
        states.push(state);
      },
      retryDelays: [10, 20],
    });
  }

  function handsOutTickets(): Promise<string> {
    ticketsAsked = ticketsAsked + 1;
    return Promise.resolve('/api/admin/order-feed?ticket=t' + ticketsAsked);
  }

  function wait(millis: number): Promise<void> {
    return new Promise(function (resolve) {
      setTimeout(resolve, millis);
    });
  }

  it('gets a ticket, opens the feed and passes each message on', async () => {
    const feed = newFeed(handsOutTickets);
    feed.start();
    await wait(0);

    expect(FakeEventSource.opened.length).toBe(1);
    expect(FakeEventSource.latest().url).toBe('/api/admin/order-feed?ticket=t1');

    FakeEventSource.latest().send({ type: 'connected', orderId: null, orderNumber: null, status: null });
    FakeEventSource.latest().send({ type: 'order-placed', orderId: 'o1', orderNumber: 'MM-000001', status: 'PENDING_PAYMENT' });

    expect(messages.map(function (m) { return m.type; })).toEqual(['connected', 'order-placed']);
    expect(states).toEqual(['connecting', 'live', 'live']);
    feed.stop();
  });

  it('after a dropped connection, closes it and opens a new feed with a NEW ticket', async () => {
    const feed = newFeed(handsOutTickets);
    feed.start();
    await wait(0);

    FakeEventSource.latest().drop();

    expect(FakeEventSource.opened[0].closed).toBe(true); // never left to retry with the used ticket
    expect(states[states.length - 1]).toBe('reconnecting');
    await wait(40);
    expect(FakeEventSource.opened.length).toBe(2);
    expect(FakeEventSource.latest().url).toBe('/api/admin/order-feed?ticket=t2');
    feed.stop();
  });

  it('keeps trying while no ticket can be had, and stop() ends it', async () => {
    let asked = 0;
    const feed = newFeed(function () {
      asked = asked + 1;
      return Promise.reject(new Error('offline'));
    });
    feed.start();
    await wait(80);
    expect(asked).toBeGreaterThanOrEqual(3);

    feed.stop();
    const askedWhenStopped = asked;
    await wait(60);
    expect(asked).toBe(askedWhenStopped);
    expect(FakeEventSource.opened.length).toBe(0);
  });
});
