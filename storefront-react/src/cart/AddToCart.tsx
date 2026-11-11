import { useState } from 'react'
import { Link } from 'react-router'
import type { StoreProduct } from '../api/client'
import { MAX_QUANTITY } from './cart'
import { useCart } from './cart-context'

// A quantity box and an "Add to cart" button, for the product page. After adding it says how many
// of this product are now in the cart, with a link to it.
export function AddToCart({ product, shopSlug }: { product: StoreProduct; shopSlug: string }) {
  const { lines, dispatch } = useCart()
  // What is typed in the box, as text. It may be empty for a moment while the customer types, so it
  // is only turned into a number when they press "Add to cart".
  const [quantityText, setQuantityText] = useState('1')

  let inCart = 0
  for (const line of lines) {
    if (line.productId === product.id) {
      inCart = line.quantity
    }
  }

  function add() {
    if (!product.id) {
      return
    }
    dispatch({
      type: 'add',
      line: {
        productId: product.id,
        slug: product.slug ?? '',
        name: product.name ?? '',
        priceMinor: product.priceMinor ?? 0,
        currency: product.currency ?? 'KES',
      },
      quantity: typedQuantity(quantityText),
    })
    setQuantityText('1')
  }

  return (
    <div className="add-to-cart">
      <label>
        Quantity
        <input
          type="number"
          min={1}
          max={MAX_QUANTITY}
          value={quantityText}
          onChange={(event) => setQuantityText(event.target.value)}
        />
      </label>
      <button type="button" className="primary-button" onClick={add} disabled={inCart >= MAX_QUANTITY}>
        Add to cart
      </button>
      {inCart > 0 && (
        <p role="status">
          {inCart} in your cart. <Link to={`/s/${shopSlug}/cart`}>View cart</Link>
        </p>
      )}
    </div>
  )
}

// The number in the box: empty or nonsense counts as 1, and the cart itself caps it at MAX_QUANTITY.
function typedQuantity(text: string): number {
  const typed = Number.parseInt(text, 10)
  if (Number.isNaN(typed) || typed < 1) {
    return 1
  }
  return typed
}
