import type { RouteObject } from 'react-router'
import { NotFoundPage } from './NotFoundPage'
import { ProductListPage } from './shop/ProductListPage'
import { ProductPage } from './shop/ProductPage'
import { ShopLayout } from './shop/ShopLayout'

// Every page of a shop sits under /s/:slug, so the shop's web name is always in the address and
// ShopLayout can load the shop once for all of them. Kept apart from main.tsx so tests can use the
// same routes with an in-memory router.
export const routes: RouteObject[] = [
  {
    path: '/s/:slug',
    element: <ShopLayout />,
    children: [
      { index: true, element: <ProductListPage /> },
      { path: 'p/:productSlug', element: <ProductPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
  {
    path: '*',
    element: (
      <main>
        <NotFoundPage />
      </main>
    ),
  },
]
