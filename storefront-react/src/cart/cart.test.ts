import { afterEach, describe, expect, it, vi } from 'vitest'
import { cartReducer, cartTotalMinor, itemCount, loadCart, MAX_LINES, MAX_QUANTITY, saveCart, storageKey } from './cart'
import type { CartLine } from './cart'

const sukuma = { productId: 'p1', slug: 'sukuma', name: 'Sukuma', priceMinor: 5000, currency: 'KES' }
const tomatoes = { productId: 'p2', slug: 'tomatoes', name: 'Tomatoes', priceMinor: 3000, currency: 'KES' }

function cartWith(...lines: CartLine[]): CartLine[] {
  return lines
}

describe('cartReducer', () => {
  it('adds a new product as its own line', () => {
    const lines = cartReducer([], { type: 'add', line: sukuma, quantity: 2 })

    expect(lines).toEqual([{ ...sukuma, quantity: 2 }])
  })

  it('adds more of a product that is already in the cart, instead of a second line', () => {
    const before = cartWith({ ...sukuma, quantity: 2 })

    const after = cartReducer(before, { type: 'add', line: sukuma, quantity: 3 })

    expect(after).toEqual([{ ...sukuma, quantity: 5 }])
    // The old cart is untouched: React compares old and new to know what changed.
    expect(before[0].quantity).toBe(2)
  })

  it('never goes above the most the backend accepts', () => {
    const lines = cartReducer(cartWith({ ...sukuma, quantity: 98 }), { type: 'add', line: sukuma, quantity: 5 })

    expect(lines[0].quantity).toBe(MAX_QUANTITY)
  })

  it('refuses a new line once the cart has the most lines an order may have', () => {
    const full: CartLine[] = []
    for (let i = 0; i < MAX_LINES; i++) {
      full.push({ ...sukuma, productId: 'p' + i, quantity: 1 })
    }

    const after = cartReducer(full, { type: 'add', line: { ...tomatoes, productId: 'new' }, quantity: 1 })

    expect(after).toHaveLength(MAX_LINES)
  })

  it('changes a quantity, and zero takes the line out', () => {
    const lines = cartWith({ ...sukuma, quantity: 2 }, { ...tomatoes, quantity: 1 })

    expect(cartReducer(lines, { type: 'set-quantity', productId: 'p1', quantity: 4 })[0].quantity).toBe(4)
    expect(cartReducer(lines, { type: 'set-quantity', productId: 'p1', quantity: 0 })).toEqual([
      { ...tomatoes, quantity: 1 },
    ])
  })

  it('removes a line and clears the cart', () => {
    const lines = cartWith({ ...sukuma, quantity: 2 }, { ...tomatoes, quantity: 1 })

    expect(cartReducer(lines, { type: 'remove', productId: 'p2' })).toEqual([{ ...sukuma, quantity: 2 }])
    expect(cartReducer(lines, { type: 'clear' })).toEqual([])
  })
})

describe('totals', () => {
  it('counts items and adds up the price in minor units', () => {
    const lines = cartWith({ ...sukuma, quantity: 2 }, { ...tomatoes, quantity: 1 })

    expect(itemCount(lines)).toBe(3)
    expect(cartTotalMinor(lines)).toBe(13000)
  })
})

describe('keeping the cart in the browser', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('keeps each shop’s cart under its own key, so two shops never mix', () => {
    saveCart('mamamboga', cartWith({ ...sukuma, quantity: 2 }))
    saveCart('kamau-hardware', cartWith({ ...tomatoes, quantity: 1 }))

    expect(loadCart('mamamboga')).toEqual([{ ...sukuma, quantity: 2 }])
    expect(loadCart('kamau-hardware')).toEqual([{ ...tomatoes, quantity: 1 }])
  })

  it('treats the shop name in any letter case as the same shop', () => {
    saveCart('MamaMboga', cartWith({ ...sukuma, quantity: 1 }))

    expect(storageKey('MamaMboga')).toBe('cart:mamamboga')
    expect(loadCart('mamamboga')).toHaveLength(1)
  })

  it('forgets the saved cart when it is emptied', () => {
    saveCart('mamamboga', cartWith({ ...sukuma, quantity: 1 }))
    saveCart('mamamboga', [])

    expect(window.localStorage.getItem('cart:mamamboga')).toBeNull()
  })

  it('starts empty when storage is blocked, and saving does not throw', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('SecurityError: storage is disabled')
    })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('QuotaExceededError')
    })

    expect(loadCart('mamamboga')).toEqual([])
    expect(() => saveCart('mamamboga', cartWith({ ...sukuma, quantity: 1 }))).not.toThrow()
  })

  it('ignores a saved cart that is not valid JSON', () => {
    window.localStorage.setItem('cart:mamamboga', '{not json')

    expect(loadCart('mamamboga')).toEqual([])
  })

  it('drops saved lines that are broken and keeps the good ones', () => {
    window.localStorage.setItem(
      'cart:mamamboga',
      JSON.stringify([{ ...sukuma, quantity: 2 }, { productId: 'p9', name: 'No price' }, 'junk', { ...tomatoes, quantity: 500 }]),
    )

    expect(loadCart('mamamboga')).toEqual([
      { ...sukuma, quantity: 2 },
      { ...tomatoes, quantity: MAX_QUANTITY },
    ])
  })
})
