import { Link } from 'react-router'
import { formatMoney } from '../money'
import { useShop } from '../shop/shop-context'
import { cartTotalMinor, MAX_QUANTITY } from './cart'
import type { CartLine } from './cart'
import { useCart } from './cart-context'

// /s/:slug/cart: what the customer is about to buy. Checkout comes with the next issue.
export function CartPage() {
  const { slug, shop } = useShop()
  const { lines, dispatch } = useCart()

  if (lines.length === 0) {
    return (
      <>
        <h1>Your cart</h1>
        <p>Your cart is empty.</p>
        <Link to={`/s/${slug}`}>Continue shopping</Link>
      </>
    )
  }

  let currency = shop.currency ?? 'KES'
  if (lines[0].currency) {
    currency = lines[0].currency
  }

  return (
    <>
      <h1>Your cart</h1>
      <ul className="cart-lines">
        {lines.map((line) => (
          <CartLineRow
            key={line.productId}
            line={line}
            shopSlug={slug}
            onQuantity={(quantity) => dispatch({ type: 'set-quantity', productId: line.productId, quantity })}
            onRemove={() => dispatch({ type: 'remove', productId: line.productId })}
          />
        ))}
      </ul>

      <p className="cart-total">
        Total <strong>{formatMoney(cartTotalMinor(lines), currency)}</strong>
      </p>
      <p className="cart-note">The shop confirms the final price when you check out.</p>

      <div className="cart-actions">
        <Link to={`/s/${slug}`}>Continue shopping</Link>
        {/* Issue #19: checkout (name, phone, pay) and the live order page. */}
        <button type="button" className="primary-button" disabled>
          Checkout (coming soon)
        </button>
      </div>
    </>
  )
}

type CartLineRowProps = {
  line: CartLine
  shopSlug: string
  onQuantity: (quantity: number) => void
  onRemove: () => void
}

function CartLineRow({ line, shopSlug, onQuantity, onRemove }: CartLineRowProps) {
  return (
    <li className="cart-line">
      <Link to={`/s/${shopSlug}/p/${line.slug}`} className="cart-line-name">
        {line.name}
      </Link>
      <span className="cart-line-price">{formatMoney(line.priceMinor, line.currency)} each</span>
      <span className="cart-line-quantity">
        <button type="button" aria-label={'One less ' + line.name} onClick={() => onQuantity(line.quantity - 1)}>
          −
        </button>
        <span aria-label={'Quantity of ' + line.name}>{line.quantity}</span>
        <button
          type="button"
          aria-label={'One more ' + line.name}
          onClick={() => onQuantity(line.quantity + 1)}
          disabled={line.quantity >= MAX_QUANTITY}
        >
          +
        </button>
      </span>
      <span className="cart-line-subtotal">{formatMoney(line.priceMinor * line.quantity, line.currency)}</span>
      <button type="button" className="link-button" onClick={onRemove}>
        Remove
      </button>
    </li>
  )
}
