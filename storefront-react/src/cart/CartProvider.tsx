import { useEffect, useReducer } from 'react'
import type { ReactNode } from 'react'
import { cartReducer, loadCart, saveCart } from './cart'
import { CartContext } from './cart-context'

// One cart per shop. ShopLayout puts this around the shop's pages with key={slug}, so another shop
// gets a brand-new provider that reads that shop's own saved cart.
export function CartProvider({ shopSlug, children }: { shopSlug: string; children: ReactNode }) {
  // The third argument reads the saved cart once, when the provider starts, instead of on every render.
  const [lines, dispatch] = useReducer(cartReducer, shopSlug, loadCart)

  // After every change, write the cart to the browser's storage, so a reload or a new tab keeps it.
  // That's what effects are for: keeping something outside React (localStorage) in step with state.
  useEffect(() => {
    saveCart(shopSlug, lines)
  }, [shopSlug, lines])

  return <CartContext.Provider value={{ lines, dispatch }}>{children}</CartContext.Provider>
}
