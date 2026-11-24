import type { RouteObject } from 'react-router'
import { CartPage } from './cart/CartPage'
import { CheckoutPage } from './checkout/CheckoutPage'
import { LandingPage } from './landing/LandingPage'
import { NotFoundPage } from './NotFoundPage'
import { OrderPage } from './orders/OrderPage'
import { ProductListPage } from './shop/ProductListPage'
import { ProductPage } from './shop/ProductPage'
import { ShopLayout } from './shop/ShopLayout'

// Every page of a shop sits under /s/:slug, so the shop's web name is always in the address and
// ShopLayout can load the shop once for all of them. Kept apart from main.tsx so tests can use the
// same routes with an in-memory router.
export const routes: RouteObject[] = [
  // The platform's own page: what it is, and the way to the admin's sign-up.
  { path: '/', element: <LandingPage /> },
  {
    path: '/s/:slug',
    element: <ShopLayout />,
    children: [
      { index: true, element: <ProductListPage /> },
      { path: 'p/:productSlug', element: <ProductPage /> },
      { path: 'cart', element: <CartPage /> },
      { path: 'checkout', element: <CheckoutPage /> },
      { path: 'orders/:orderNumber', element: <OrderPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
  {
    path: '*',
    element: (
      <main className="message-page">
        <NotFoundPage />
      </main>
    ),
  },
]
