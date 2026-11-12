import { Link, useParams } from 'react-router'
import { useShop } from '../shop/shop-context'

// /s/:slug/orders/:orderNumber. For now only confirms the order; the next commit adds the order's
// details and the live payment status.
export function OrderPage() {
  const { slug } = useShop()
  const params = useParams()
  const orderNumber = params.orderNumber ?? ''

  return (
    <>
      <h1>Order {orderNumber}</h1>
      <p>Your order is placed. Check your phone and enter your M-Pesa PIN.</p>
      <Link to={`/s/${slug}`}>Continue shopping</Link>
    </>
  )
}
