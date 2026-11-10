# 0010: Live order status over SSE, opened with one-time stream tickets

Status: Accepted (2026-11-10)

## Context

After paying, a customer watches the order page until "Payment received" appears. Asking the server
every few seconds (polling) is slow to notice changes and wastes requests. The information only flows
one way, server to browser, so Server-Sent Events (SSE) fit: one long-lived HTTP response that the
server writes a line to whenever something changes. The browser's `EventSource` reconnects by itself.

But `EventSource` can't send an `Authorization` header, and guests have no login anyway. Their proof
of ownership is the private order-link token they got when ordering. That token must not go in a URL:
URLs end up in server logs, proxy logs and browser history, and the token is a long-lived key.

The architecture (section 9) asks us to choose between a one-time ticket and a fetch-based SSE client
library, and to record the choice.

## Decision

**A one-time stream ticket.**

1. The page asks for a ticket with an ordinary request that CAN prove who it is:
   - customers: `POST /api/store/{slug}/orders/{orderNumber}/stream-ticket` with the order link in the
     JSON body;
   - staff: `POST /api/admin/orders/{orderId}/stream-ticket` with their login.

   A wrong link, or another shop's order, gets 404 either way.
2. The ticket is 32 random bytes. Only its SHA-256 is stored (`notification.stream_tickets`). It is for
   one order, expires after 60 seconds and works once. It is used up with one conditional `UPDATE`, so
   two tabs can't share it.
3. The page opens `new EventSource("/api/store/order-stream?ticket=...")`.
   - The first message is the order's current status, so a late or reconnecting page shows the truth
     at once.
   - Each change follows (from `OrderStatusChangedEvent`, and from retryable `PaymentFailedEvent`).
   - The server closes the stream after a final status (PAID, CANCELLED, EXPIRED, FULFILLED).
   - A `:keep-alive` comment goes out every 20 seconds, so proxies don't cut quiet connections.
4. A dropped connection means a new ticket, then a new stream.

The stream URL carries the ticket only, not the shop and order number as the Guide sketched. The
ticket already names them, and staff can use the same stream without knowing the shop's web name.

A fetch-based SSE library would let the page send a header instead, but it adds a dependency to both
frontends and loses `EventSource`'s built-in reconnect. The ticket keeps the browser side to plain
`EventSource`.

## Consequences

- A ticket that leaks (logs, history) is useless: it has expired, or it has already been used.
- One extra request before each stream, and after each reconnect. Cheap.
- **Scaling: the open streams live in one app instance's memory** (`OrderStreams`). With two or more
  instances behind a load balancer, the event can be handled by an instance that doesn't hold the
  customer's connection, and the customer sees nothing. The fix, when we get there: every instance
  publishes each status message to a shared broker (Redis pub/sub, or Postgres `LISTEN/NOTIFY`), and
  every instance subscribes and forwards it to the streams it holds. Not built now; we run one instance.
- An open stream costs an open connection, not a busy thread: Spring MVC hands the request thread
  back while the stream waits. A stream lasts ten minutes; then the browser reconnects with a new
  ticket.
