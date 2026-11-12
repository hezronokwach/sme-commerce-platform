import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { api } from '../api/client'
import { readProblem } from '../api/problem'
import type { Problem } from '../api/problem'
import { cartTotalMinor } from '../cart/cart'
import type { CartLine } from '../cart/cart'
import { useCart } from '../cart/cart-context'
import { formatMoney } from '../money'
import { saveOrderLink } from '../orders/order-links'
import { useShop } from '../shop/shop-context'
import { configuredPaymentMethod } from './payment-method'
import { normalisePhone } from './phone'

// /s/:slug/checkout (workflow W6 steps 1 to 5): name and phone, then the order is placed and the
// customer goes to the order page, where the PIN prompt and its outcome are shown live.
//
// Only product ids and quantities are sent. The server prices the order itself, so a price changed in
// the browser can't change what the customer pays.

export function CheckoutPage() {
  const { slug, shop } = useShop()
  const { lines, dispatch } = useCart()
  const navigate = useNavigate()
  const paymentMethod = configuredPaymentMethod()

  const [name, setName] = useState('')
  const [phone, setPhone] = useState('')
  const [phoneError, setPhoneError] = useState('')
  const [nameError, setNameError] = useState('')
  const [orderError, setOrderError] = useState('')
  const [sending, setSending] = useState(false)

  if (lines.length === 0) {
    return (
      <>
        <h1>Checkout</h1>
        <p>Your cart is empty.</p>
        <Link to={`/s/${slug}`}>Continue shopping</Link>
      </>
    )
  }

  async function placeOrder(event: FormEvent) {
    event.preventDefault()
    if (sending) {
      return // a second tap while the first is on its way
    }

    // Check in the browser first, so a typo is pointed out at once.
    let valid = true
    if (name.trim() === '') {
      setNameError('Enter your name.')
      valid = false
    } else {
      setNameError('')
    }
    const normalised = normalisePhone(phone)
    if (normalised === null) {
      setPhoneError('Enter a Kenyan mobile number, for example 0712 345 678.')
      valid = false
    } else {
      setPhoneError('')
    }
    if (!valid || normalised === null) {
      return
    }

    setSending(true)
    setOrderError('')
    const orderLines: { productId: string; quantity: number }[] = []
    for (const line of lines) {
      orderLines.push({ productId: line.productId, quantity: line.quantity })
    }

    try {
      const { data, error, response } = await api.POST('/api/store/{slug}/orders', {
        params: { path: { slug } },
        body: { customerName: name.trim(), customerPhone: normalised, paymentMethod, lines: orderLines },
      })

      if (response.status === 201 && data && data.orderNumber && data.accessToken) {
        // Keep the private link in this browser, then empty the cart: the goods are now in the order.
        saveOrderLink(slug, { orderNumber: data.orderNumber, accessToken: data.accessToken })
        dispatch({ type: 'clear' })
        // The token also travels in the page's history state (never the address), in case the
        // browser can't store it.
        navigate(`/s/${slug}/orders/${data.orderNumber}`, { state: { accessToken: data.accessToken } })
        return
      }

      const problem = readProblem(error)
      if (problem.code === 'PHONE_INVALID') {
        setPhoneError('Enter a Kenyan mobile number, for example 0712 345 678.')
      } else {
        setOrderError(explainProblem(problem, lines))
      }
    } catch {
      setOrderError("We couldn't reach the shop. Check your connection and try again.")
    }
    setSending(false)
  }

  let buttonText = 'Pay with M-Pesa'
  if (sending) {
    buttonText = 'Placing your order...'
  }
  let currency = shop.currency ?? 'KES'
  if (lines[0].currency) {
    currency = lines[0].currency
  }

  return (
    <>
      <h1>Checkout</h1>

      <section className="checkout-summary" aria-label="Your order">
        <ul>
          {lines.map((line) => (
            <li key={line.productId}>
              <span>
                {line.quantity} × {line.name}
              </span>
              <span>{formatMoney(line.priceMinor * line.quantity, line.currency)}</span>
            </li>
          ))}
        </ul>
        <p className="cart-total">
          Total <strong>{formatMoney(cartTotalMinor(lines), currency)}</strong>
        </p>
      </section>

      <form className="checkout-form" onSubmit={placeOrder} noValidate>
        <label>
          Your name
          <input
            name="name"
            autoComplete="name"
            maxLength={120}
            value={name}
            onChange={(event) => setName(event.target.value)}
            aria-invalid={nameError !== ''}
          />
        </label>
        {nameError && <p className="field-error">{nameError}</p>}

        <label>
          M-Pesa phone number
          <input
            name="phone"
            type="tel"
            inputMode="tel"
            autoComplete="tel"
            placeholder="0712 345 678"
            maxLength={20}
            value={phone}
            onChange={(event) => setPhone(event.target.value)}
            aria-invalid={phoneError !== ''}
          />
        </label>
        {phoneError && <p className="field-error">{phoneError}</p>}

        {orderError && (
          <p role="alert" className="form-error">
            {orderError}
          </p>
        )}

        <button type="submit" className="primary-button" disabled={sending}>
          {buttonText}
        </button>
        <p className="cart-note">You will get a prompt on your phone to enter your M-Pesa PIN.</p>
        {paymentMethod === 'FAKE' && (
          <p className="test-note">
            Test payments: no real money moves. The last digit of the phone decides: 0 pays, 1 cancels, 2 is a
            wrong PIN, 3 never answers, 4 is declined.
          </p>
        )}
      </form>
    </>
  )
}

// What to tell the customer when the order was refused, chosen by the problem's code.
function explainProblem(problem: Problem, lines: CartLine[]): string {
  if (problem.code === 'INSUFFICIENT_STOCK') {
    const name = productName(problem.productId, lines)
    if (problem.available === 0) {
      return name + ' is sold out. Remove it from your cart to continue.'
    }
    return 'Only ' + problem.available + ' left of ' + name + '. Lower the quantity in your cart to continue.'
  }
  if (problem.code === 'PRODUCT_UNAVAILABLE') {
    return productName(problem.productId, lines) + ' is no longer for sale. Remove it from your cart to continue.'
  }
  if (problem.code === 'SHOP_NOT_FOUND') {
    return 'This shop is not taking orders right now.'
  }
  if (problem.detail) {
    return problem.detail
  }
  return 'Something went wrong. Please try again.'
}

function productName(productId: string | undefined, lines: CartLine[]): string {
  for (const line of lines) {
    if (line.productId === productId) {
      return line.name
    }
  }
  return 'One of the products'
}
