import { useOutletContext } from 'react-router'
import type { ShopProfile } from '../api/client'

// What the pages inside ShopLayout (product list, product page, cart) get from it: the shop's web
// name from the address, and the shop's profile, already loaded.
export type ShopContext = {
  slug: string
  shop: ShopProfile
}

export function useShop(): ShopContext {
  return useOutletContext<ShopContext>()
}
