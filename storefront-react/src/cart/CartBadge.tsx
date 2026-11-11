import { Link } from 'react-router'
import { itemCount } from './cart'
import { useCart } from './cart-context'

// The cart link in the shop's header, with how many items are in it.
export function CartBadge({ slug }: { slug: string }) {
  const { lines } = useCart()
  const count = itemCount(lines)

  let label = 'Cart, empty'
  if (count === 1) {
    label = 'Cart, 1 item'
  } else if (count > 1) {
    label = 'Cart, ' + count + ' items'
  }

  return (
    <Link to={`/s/${slug}/cart`} className="cart-badge" aria-label={label}>
      Cart
      {count > 0 && <span className="cart-count">{count}</span>}
    </Link>
  )
}
