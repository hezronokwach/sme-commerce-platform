import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { api } from '../api/client'
import type { StoreCategory, StoreProduct } from '../api/client'
import { formatMoney } from '../money'
import { ProductImage } from './ProductImage'
import { useShop } from './shop-context'

// Workflow W5 step 4: the shop's products on sale. The chosen category is kept in the address
// (?category=...), so the back button and a shared link both show the same list.

type ListState =
  | { kind: 'loading' }
  | { kind: 'error' }
  | { kind: 'ready'; products: StoreProduct[] }

export function ProductListPage() {
  const { slug } = useShop()
  const [searchParams, setSearchParams] = useSearchParams()
  const categoryId = searchParams.get('category') ?? ''

  function chooseCategory(id: string) {
    if (id) {
      setSearchParams({ category: id })
    } else {
      setSearchParams({})
    }
  }

  return (
    <>
      <CategoryMenu slug={slug} chosenId={categoryId} onChoose={chooseCategory} />
      {/* A new key for each category, so each list starts at "loading" (see ShopLayout). */}
      <ProductGrid key={categoryId} slug={slug} categoryId={categoryId} />
    </>
  )
}

// The menu: loaded once per shop. Without it the page still works, just without the filter.
function CategoryMenu(props: { slug: string; chosenId: string; onChoose: (id: string) => void }) {
  const { slug, chosenId, onChoose } = props
  const [categories, setCategories] = useState<StoreCategory[]>([])

  useEffect(() => {
    let stillWanted = true

    async function loadCategories() {
      try {
        const { data } = await api.GET('/api/store/{slug}/categories', { params: { path: { slug } } })
        if (stillWanted && data) {
          setCategories(data)
        }
      } catch {
        // Leave the menu empty.
      }
    }
    loadCategories()

    return () => {
      stillWanted = false
    }
  }, [slug])

  if (categories.length === 0) {
    return null
  }
  return (
    <nav className="category-menu" aria-label="Categories">
      <button type="button" aria-pressed={chosenId === ''} onClick={() => onChoose('')}>
        All
      </button>
      {categories.map((category) => (
        <button
          key={category.id}
          type="button"
          aria-pressed={category.id === chosenId}
          onClick={() => onChoose(category.id ?? '')}
        >
          {category.name}
        </button>
      ))}
    </nav>
  )
}

// The products on sale, all of them or one category's ('' means all).
function ProductGrid({ slug, categoryId }: { slug: string; categoryId: string }) {
  const [state, setState] = useState<ListState>({ kind: 'loading' })

  useEffect(() => {
    let stillWanted = true

    async function loadProducts() {
      const query: { categoryId?: string } = {}
      if (categoryId) {
        query.categoryId = categoryId
      }
      try {
        const { data } = await api.GET('/api/store/{slug}/products', {
          params: { path: { slug }, query: query },
        })
        if (!stillWanted) {
          return
        }
        if (data) {
          setState({ kind: 'ready', products: data })
        } else {
          setState({ kind: 'error' })
        }
      } catch {
        if (stillWanted) {
          setState({ kind: 'error' })
        }
      }
    }
    loadProducts()

    return () => {
      stillWanted = false
    }
  }, [slug, categoryId])

  if (state.kind === 'loading') {
    return <p aria-busy="true">Loading products...</p>
  }
  if (state.kind === 'error') {
    return <p role="alert">We couldn't load the products. Try again in a moment.</p>
  }
  if (state.products.length === 0) {
    return <p>Nothing on sale here yet.</p>
  }
  return (
    <ul className="product-grid">
      {state.products.map((product) => (
        <li key={product.id}>
          <Link to={`/s/${slug}/p/${product.slug}`} className="product-card">
            <ProductImage product={product} />
            <span className="product-name">{product.name}</span>
            <span className="product-price">{formatMoney(product.priceMinor ?? 0, product.currency ?? 'KES')}</span>
          </Link>
        </li>
      ))}
    </ul>
  )
}
