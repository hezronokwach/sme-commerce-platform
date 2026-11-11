import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { api } from '../api/client'
import type { StoreProduct } from '../api/client'
import { AddToCart } from '../cart/AddToCart'
import { formatMoney } from '../money'
import { ProductImage } from './ProductImage'
import { useShop } from './shop-context'

// One product, found by its web name: /s/mamamboga/p/sukuma-bundle. A draft, archived or unknown
// product gets 404 from the backend, and the page says so.

type ProductState =
  | { kind: 'loading' }
  | { kind: 'not-found' }
  | { kind: 'error' }
  | { kind: 'ready'; product: StoreProduct }

export function ProductPage() {
  const { slug } = useShop()
  const params = useParams()
  const productSlug = params.productSlug ?? ''
  // A new key for each product, so each one starts at "loading" (see ShopLayout).
  return <LoadedProduct key={productSlug} slug={slug} productSlug={productSlug} />
}

function LoadedProduct({ slug, productSlug }: { slug: string; productSlug: string }) {
  const [state, setState] = useState<ProductState>({ kind: 'loading' })

  useEffect(() => {
    let stillWanted = true

    async function loadProduct() {
      try {
        const { data, response } = await api.GET('/api/store/{slug}/products/{productSlug}', {
          params: { path: { slug, productSlug } },
        })
        if (!stillWanted) {
          return
        }
        if (response.status === 404) {
          setState({ kind: 'not-found' })
        } else if (data) {
          setState({ kind: 'ready', product: data })
        } else {
          setState({ kind: 'error' })
        }
      } catch {
        if (stillWanted) {
          setState({ kind: 'error' })
        }
      }
    }
    loadProduct()

    return () => {
      stillWanted = false
    }
  }, [slug, productSlug])

  const backToShop = <Link to={`/s/${slug}`}>Back to all products</Link>

  if (state.kind === 'loading') {
    return <p aria-busy="true">Loading...</p>
  }
  if (state.kind === 'not-found') {
    return (
      <>
        <h1>Product not found</h1>
        <p>This product isn't for sale here any more.</p>
        {backToShop}
      </>
    )
  }
  if (state.kind === 'error') {
    return (
      <>
        <p role="alert">We couldn't load this product. Try again in a moment.</p>
        {backToShop}
      </>
    )
  }

  const product = state.product
  return (
    <article className="product-page">
      {backToShop}
      <ProductImage product={product} />
      <h1>{product.name}</h1>
      <p className="product-price">{formatMoney(product.priceMinor ?? 0, product.currency ?? 'KES')}</p>
      {product.description && <p className="product-description">{product.description}</p>}
      <AddToCart product={product} shopSlug={slug} />
    </article>
  )
}
