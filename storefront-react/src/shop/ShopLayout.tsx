import { useEffect, useState } from 'react'
import type { CSSProperties } from 'react'
import { Link, Outlet, useParams } from 'react-router'
import { api } from '../api/client'
import type { ShopProfile } from '../api/client'
import type { ShopContext } from './shop-context'

// Workflow W5 steps 2 and 3: the shop comes from the address (/s/mamamboga). Its profile is loaded
// first, before anything else on the page. An unknown or suspended shop gets 404 from the backend, and
// then the page says "Shop not found" and never asks for products.

type ShopState =
  | { kind: 'loading' }
  | { kind: 'not-found' }
  | { kind: 'error' }
  | { kind: 'ready'; shop: ShopProfile }

export function ShopLayout() {
  const params = useParams()
  const slug = params.slug ?? ''
  // key={slug}: moving to another shop gives a brand-new LoadedShop, which starts at "loading" again.
  // Without the key React would keep the old shop's state until the new answer arrived.
  return <LoadedShop key={slug} slug={slug} />
}

function LoadedShop({ slug }: { slug: string }) {
  const [state, setState] = useState<ShopState>({ kind: 'loading' })

  useEffect(() => {
    // If this page is left before the answer arrives, the answer is ignored.
    let stillWanted = true

    async function loadShop() {
      try {
        const { data, response } = await api.GET('/api/store/{slug}', { params: { path: { slug } } })
        if (!stillWanted) {
          return
        }
        if (response.status === 404) {
          setState({ kind: 'not-found' })
        } else if (data) {
          setState({ kind: 'ready', shop: data })
        } else {
          setState({ kind: 'error' })
        }
      } catch {
        // No connection, or the backend is down.
        if (stillWanted) {
          setState({ kind: 'error' })
        }
      }
    }
    loadShop()

    return () => {
      stillWanted = false
    }
  }, [slug])

  if (state.kind === 'loading') {
    return <main aria-busy="true">Loading the shop...</main>
  }
  if (state.kind === 'not-found') {
    return (
      <main>
        <h1>Shop not found</h1>
        <p>There is no shop at this address. Check the link you were sent.</p>
      </main>
    )
  }
  if (state.kind === 'error') {
    return (
      <main>
        <h1>Something went wrong</h1>
        <p>We couldn't load this shop. Check your connection and try again.</p>
        <button type="button" onClick={() => window.location.reload()}>
          Try again
        </button>
      </main>
    )
  }

  const shop = state.shop
  const context: ShopContext = { slug, shop }
  return (
    <div className="shop" style={shopColours(shop)}>
      <header className="shop-header">
        <Link to={`/s/${slug}`} className="shop-name">
          {shop.logoUrl && <img src={shop.logoUrl} alt="" className="shop-logo" />}
          <span>{shop.name}</span>
        </Link>
        {shop.tagline && <p className="shop-tagline">{shop.tagline}</p>}
      </header>
      <main>
        <Outlet context={context} />
      </main>
    </div>
  )
}

// The owner's colours replace the default --primary and --accent for everything inside the shop.
function shopColours(shop: ShopProfile): CSSProperties {
  const colours: Record<string, string> = {}
  if (shop.primaryColor) {
    colours['--primary'] = shop.primaryColor
  }
  if (shop.accentColor) {
    colours['--accent'] = shop.accentColor
  }
  return colours as CSSProperties
}
