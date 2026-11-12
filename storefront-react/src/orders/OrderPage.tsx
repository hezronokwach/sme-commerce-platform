import { useEffect, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router'
import { api } from '../api/client'
import type { components } from '../api/schema'
import { readProblem } from '../api/problem'
import { formatMoney } from '../money'
import { useShop } from '../shop/shop-context'
import { watchOrder } from './live-status'
import type { StatusMessage } from './live-status'
import { findOrderToken } from './order-links'
import { headingFor, isFinal, viewFor } from './order-view'
import type { OrderViewKind } from './order-view'

// /s/:slug/orders/:orderNumber (workflows W6 step 7 and W7): the order, and how its payment is going,
// updated live while the customer enters their PIN. No polling: the server pushes each change.

type CustomerOrder = components['schemas']['CustomerOrderResponse']

export function OrderPage() {
  const { slug } = useShop()
  const params = useParams()
  const location = useLocation()
  const orderNumber = params.orderNumber ?? ''

  // The private order link: saved in this browser at checkout, or handed over in the page's history
  // state (when the browser couldn't store it). Never in the address.
  let token = findOrderToken(slug, orderNumber)
  if (token === null && location.state && typeof location.state.accessToken === 'string') {
    token = location.state.accessToken
  }

  if (token === null) {
    return (
      <>
        <h1>Order {orderNumber}</h1>
        <p>Open this order on the phone or computer you ordered with. Its private link is kept there.</p>
        <Link to={`/s/${slug}`}>Continue shopping</Link>
      </>
    )
  }
  // A new key for each order, so each one starts fresh (see ShopLayout).
  return <LiveOrder key={orderNumber} slug={slug} orderNumber={orderNumber} token={token} />
}

type LoadState =
  | { kind: 'loading' }
  | { kind: 'not-found' }
  | { kind: 'error' }
  | { kind: 'ready'; order: CustomerOrder }

// The newest news about the payment: first from loading the order, then from the live stream.
type PaymentNews = {
  status: string
  paymentStatus: string | null
  message: string
}

function LiveOrder({ slug, orderNumber, token }: { slug: string; orderNumber: string; token: string }) {
  const [load, setLoad] = useState<LoadState>({ kind: 'loading' })
  const [news, setNews] = useState<PaymentNews | null>(null)
  const [reconnecting, setReconnecting] = useState(false)
  const [retryError, setRetryError] = useState('')
  const [retrying, setRetrying] = useState(false)

  // 1. The order itself: lines, total, deadline, and how its payment stands right now.
  useEffect(() => {
    let stillWanted = true

    async function loadOrder() {
      try {
        const { data, response } = await api.GET('/api/store/{slug}/orders/{orderNumber}', {
          params: { path: { slug, orderNumber }, header: { 'X-Order-Token': token } },
        })
        if (!stillWanted) {
          return
        }
        if (response.status === 404) {
          setLoad({ kind: 'not-found' })
        } else if (data) {
          setLoad({ kind: 'ready', order: data })
        } else {
          setLoad({ kind: 'error' })
        }
      } catch {
        if (stillWanted) {
          setLoad({ kind: 'error' })
        }
      }
    }
    loadOrder()

    return () => {
      stillWanted = false
    }
  }, [slug, orderNumber, token])

  // 2. Once loaded, and while the payment isn't over, watch it live.
  const loadedOrder = load.kind === 'ready'
  let finalWhenLoaded = false
  if (load.kind === 'ready') {
    finalWhenLoaded = isFinal(viewFor(load.order.status, load.order.paymentStatus))
  }
  useEffect(() => {
    if (!loadedOrder || finalWhenLoaded) {
      return
    }
    function showStatus(message: StatusMessage) {
      setNews({ status: message.status, paymentStatus: message.paymentStatus, message: message.message })
    }
    const stop = watchOrder({
      shopSlug: slug,
      orderNumber,
      orderToken: token,
      onStatus: showStatus,
      onReconnecting: setReconnecting,
    })
    return stop
  }, [loadedOrder, finalWhenLoaded, slug, orderNumber, token])

  if (load.kind === 'loading') {
    return <p aria-busy="true">Loading your order...</p>
  }
  if (load.kind === 'not-found') {
    return (
      <>
        <h1>Order not found</h1>
        <p>We couldn't find order {orderNumber} in this shop.</p>
        <Link to={`/s/${slug}`}>Continue shopping</Link>
      </>
    )
  }
  if (load.kind === 'error') {
    return (
      <>
        <h1>Order {orderNumber}</h1>
        <p role="alert">We couldn't load your order. Check your connection and refresh the page.</p>
      </>
    )
  }

  const order = load.order
  let orderStatus = order.status
  let paymentStatus: string | null | undefined = order.paymentStatus
  if (news) {
    orderStatus = news.status
    paymentStatus = news.paymentStatus
  }
  const kind = viewFor(orderStatus, paymentStatus)

  async function tryAgain() {
    if (retrying) {
      return
    }
    setRetrying(true)
    setRetryError('')
    try {
      const { error, response } = await api.POST('/api/store/{slug}/orders/{orderNumber}/payment-attempts', {
        params: { path: { slug, orderNumber } },
        body: { orderToken: token },
      })
      if (response.status === 202) {
        // Saved; the new PIN prompt is on its way. The stream will confirm, then say how it ended.
        setNews({
          status: 'PENDING_PAYMENT',
          paymentStatus: 'INITIATED',
          message: 'Waiting for payment. Check your phone and enter your PIN.',
        })
      } else {
        const problem = readProblem(error)
        let text = 'We couldn\'t start a new payment. Please try again in a moment.'
        if (problem.detail) {
          text = problem.detail
        }
        setRetryError(text)
      }
    } catch {
      setRetryError("We couldn't reach the shop. Check your connection and try again.")
    }
    setRetrying(false)
  }

  return (
    <article className={'order-page order-' + kind}>
      <p className="order-number">Order {order.orderNumber}</p>

      <section className="order-status" aria-live="polite">
        <h1>{headingFor(kind)}</h1>
        <p>{detailFor(kind, order, news)}</p>
        {kind === 'waiting' && order.expiresAt && <TimeLeft expiresAt={order.expiresAt} />}
        {kind === 'failed' && (
          <button type="button" className="primary-button" onClick={tryAgain} disabled={retrying}>
            Try again
          </button>
        )}
        {retryError && (
          <p role="alert" className="form-error">
            {retryError}
          </p>
        )}
        {reconnecting && !isFinal(kind) && <p className="reconnecting">Reconnecting...</p>}
      </section>

      <section className="order-lines" aria-label="What you ordered">
        <ul>
          {(order.lines ?? []).map((line, index) => (
            <li key={index}>
              <span>
                {line.quantity} × {line.productName}
              </span>
              <span>{formatMoney(line.lineTotalMinor ?? 0, order.currency ?? 'KES')}</span>
            </li>
          ))}
        </ul>
        <p className="cart-total">
          Total <strong>{formatMoney(order.totalMinor ?? 0, order.currency ?? 'KES')}</strong>
        </p>
      </section>

      <Link to={`/s/${slug}`}>Continue shopping</Link>
    </article>
  )
}

// The sentence under the heading. The server's own words when the stream has spoken; otherwise words
// built from the loaded order.
function detailFor(kind: OrderViewKind, order: CustomerOrder, news: PaymentNews | null): string {
  if (news && news.message) {
    return news.message
  }
  if (kind === 'waiting') {
    return 'We sent a payment prompt to ' + (order.phoneHint ?? 'your phone') + '. Enter your M-Pesa PIN to pay.'
  }
  if (kind === 'failed') {
    if (order.paymentFailureReason) {
      return order.paymentFailureReason + ' You can try again.'
    }
    return 'The payment did not go through. You can try again.'
  }
  if (kind === 'checking') {
    return 'This can take a minute. Please don\'t pay again.'
  }
  if (kind === 'paid') {
    return 'Payment received. The shop will get your order ready.'
  }
  if (kind === 'cancelled') {
    return 'This order was cancelled.'
  }
  return 'The time to pay ran out, so this order was cancelled.'
}

// "12:34 left to pay", counting down each second. When it reaches zero the page does NOT say "expired":
// only the server decides that, because a payment can still arrive after the deadline.
function TimeLeft({ expiresAt }: { expiresAt: string }) {
  // A function, so the clock is read once when the countdown appears, not on every render.
  const [now, setNow] = useState(readClock)

  useEffect(() => {
    const timer = setInterval(() => setNow(readClock()), 1000)
    return () => clearInterval(timer)
  }, [])

  const secondsLeft = Math.floor((new Date(expiresAt).getTime() - now) / 1000)
  if (secondsLeft <= 0) {
    return <p className="time-left">The time to pay is up. If you entered your PIN, we are still checking.</p>
  }
  const minutes = Math.floor(secondsLeft / 60)
  const seconds = secondsLeft % 60
  let secondsText = String(seconds)
  if (seconds < 10) {
    secondsText = '0' + seconds
  }
  return <p className="time-left">{minutes}:{secondsText} left to pay</p>
}

function readClock(): number {
  return Date.now()
}
