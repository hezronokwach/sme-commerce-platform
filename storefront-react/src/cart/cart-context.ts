import { createContext, useContext } from 'react'
import type { CartAction, CartLine } from './cart'

// What the pages of a shop can do with its cart. Given by CartProvider (inside ShopLayout).
export type CartContextValue = {
  lines: CartLine[]
  dispatch: (action: CartAction) => void
}

export const CartContext = createContext<CartContextValue | null>(null)

export function useCart(): CartContextValue {
  const cart = useContext(CartContext)
  if (cart === null) {
    throw new Error('useCart is used outside a shop (no CartProvider above it)')
  }
  return cart
}
