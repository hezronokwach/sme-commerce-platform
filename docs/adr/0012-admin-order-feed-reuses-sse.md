# 0012: The admin's live order feed reuses SSE, not STOMP

Status: Accepted (2026-11-12)

## Context

Staff keep the admin's orders page open while customers buy. A new order should appear there without
anyone pressing refresh (issue "Admin live order feed"). The Guide (6.4) sketches STOMP over WebSocket:
a `/ws` endpoint, the token checked on CONNECT, a topic per shop, and an Angular STOMP client that
reconnects. The plan marks this a stretch item and says "if you are behind, skip and reuse SSE".

What the feed needs:

- one direction only, server to browser ("something happened to one of your orders");
- only the shop's own staff may receive it;
- it must survive dropped connections.

SSE with one-time tickets (ADR 0010) already does all three for the customer's order page.

## Decision

**Reuse SSE.** The admin gets one stream per shop, opened the same way as the customer's per-order stream:

1. The orders page asks for a ticket with its normal staff login: `POST /api/admin/order-feed/ticket`.
   The shop comes from the access token, never from the request.
2. It opens `new EventSource("/api/admin/order-feed?ticket=...")`. Only this one GET under `/api/admin`
   is let through without a token (EventSource can't send one); the ticket proves who it is.
3. A ticket now has a scope (V71): ORDER for one order, SHOP for a shop's feed. Each stream only accepts
   its own kind, checked in the same single UPDATE that uses the ticket up.
4. The feed sends small messages, `{type, orderId, orderNumber, status}`, for a new order and for each
   later status change. The page then **reloads its list** quietly and highlights the new order. It doesn't
   build rows from the message, so the list's filter, pages and names stay what the server says.

`OrderStreams` and the new `ShopFeeds` share one base class, `LiveStreams`, for the bookkeeping and the
keep-alive.

## Consequences

- No new library on either side, and one way of doing live updates in the whole app.
- One extra list request per order event. At a small shop's volume that's nothing.
- Same scaling note as ADR 0010: the open streams live in one instance's memory; several instances need a
  shared broker (Redis pub/sub or Postgres `LISTEN/NOTIFY`).
- We give up two-way messages. If the admin ever needs to *send* over the same connection (for example
  "I'm handling this order" shown to other staff), switch to STOMP then: the feed's messages and the
  page's "reload on news" logic stay; only the transport and the subscribe step change.
