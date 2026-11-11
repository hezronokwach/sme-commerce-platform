// The cart (workflow W5 step 5). It lives only in the customer's browser until checkout: no login,
// nothing on the server. Everything here is plain functions, so it can be tested without a page.
//
// The price in a cart line is only for showing. At checkout the browser sends product ids and
// quantities, and ordering asks catalog for the current prices (W6 step 2). An old or edited price in
// the browser can never change what the customer pays.

export type CartLine = {
  productId: string
  slug: string
  name: string
  priceMinor: number
  currency: string
  quantity: number
}

// The same limits the backend checks when an order is placed (PlaceOrderRequest), so a full cart is
// never refused at checkout for being too big.
export const MAX_QUANTITY = 99
export const MAX_LINES = 50

export type CartAction =
  | { type: 'add'; line: Omit<CartLine, 'quantity'>; quantity: number }
  | { type: 'set-quantity'; productId: string; quantity: number }
  | { type: 'remove'; productId: string }
  | { type: 'clear' }

// Gives the cart after an action. It never changes the old list; it builds a new one, which is how
// React notices that something changed.
export function cartReducer(lines: CartLine[], action: CartAction): CartLine[] {
  if (action.type === 'add') {
    return addToCart(lines, action.line, action.quantity)
  }
  if (action.type === 'set-quantity') {
    return setQuantity(lines, action.productId, action.quantity)
  }
  if (action.type === 'remove') {
    return removeLine(lines, action.productId)
  }
  if (action.type === 'clear') {
    return []
  }
  return lines
}

// A product already in the cart gets more; a new one gets its own line (if there's room for one).
function addToCart(lines: CartLine[], line: Omit<CartLine, 'quantity'>, quantity: number): CartLine[] {
  const result: CartLine[] = []
  let found = false
  for (const existing of lines) {
    if (existing.productId === line.productId) {
      found = true
      result.push({ ...existing, quantity: limitQuantity(existing.quantity + quantity) })
    } else {
      result.push(existing)
    }
  }
  if (!found) {
    if (lines.length >= MAX_LINES) {
      return lines
    }
    result.push({ ...line, quantity: limitQuantity(quantity) })
  }
  return result
}

// Zero or less takes the line out, like pressing "Remove".
function setQuantity(lines: CartLine[], productId: string, quantity: number): CartLine[] {
  if (quantity <= 0) {
    return removeLine(lines, productId)
  }
  const result: CartLine[] = []
  for (const existing of lines) {
    if (existing.productId === productId) {
      result.push({ ...existing, quantity: limitQuantity(quantity) })
    } else {
      result.push(existing)
    }
  }
  return result
}

function removeLine(lines: CartLine[], productId: string): CartLine[] {
  const result: CartLine[] = []
  for (const existing of lines) {
    if (existing.productId !== productId) {
      result.push(existing)
    }
  }
  return result
}

function limitQuantity(quantity: number): number {
  if (quantity < 1) {
    return 1
  }
  if (quantity > MAX_QUANTITY) {
    return MAX_QUANTITY
  }
  return Math.floor(quantity)
}

// How many items, for the badge: 2 sukuma + 1 tomatoes = 3.
export function itemCount(lines: CartLine[]): number {
  let count = 0
  for (const line of lines) {
    count = count + line.quantity
  }
  return count
}

// In minor units. A shop has one currency, so every line has the same one.
export function cartTotalMinor(lines: CartLine[]): number {
  let total = 0
  for (const line of lines) {
    total = total + line.priceMinor * line.quantity
  }
  return total
}

// --- keeping the cart in the browser -----------------------------------------------------------------

// One key per shop: cart:mamamboga and cart:kamau-hardware never mix. Lower case, because the backend
// treats /s/MamaMboga and /s/mamamboga as the same shop.
export function storageKey(shopSlug: string): string {
  return 'cart:' + shopSlug.toLowerCase()
}

// localStorage can throw: a private window, storage turned off, or a full disk. Then the cart still
// works for this visit; it just isn't remembered. And what was saved may have been edited by hand or
// left by an older version of this page, so each line is checked and bad ones are dropped.
export function loadCart(shopSlug: string): CartLine[] {
  let saved: string | null
  try {
    saved = window.localStorage.getItem(storageKey(shopSlug))
  } catch {
    return []
  }
  if (!saved) {
    return []
  }

  let parsed: unknown
  try {
    parsed = JSON.parse(saved)
  } catch {
    return []
  }
  if (!Array.isArray(parsed)) {
    return []
  }

  const lines: CartLine[] = []
  for (const item of parsed) {
    if (isCartLine(item) && lines.length < MAX_LINES) {
      lines.push({ ...item, quantity: limitQuantity(item.quantity) })
    }
  }
  return lines
}

export function saveCart(shopSlug: string, lines: CartLine[]): void {
  try {
    if (lines.length === 0) {
      window.localStorage.removeItem(storageKey(shopSlug))
    } else {
      window.localStorage.setItem(storageKey(shopSlug), JSON.stringify(lines))
    }
  } catch {
    // Not remembered, but nothing breaks.
  }
}

function isCartLine(item: unknown): item is CartLine {
  if (typeof item !== 'object' || item === null) {
    return false
  }
  const line = item as Record<string, unknown>
  return (
    typeof line.productId === 'string' &&
    typeof line.slug === 'string' &&
    typeof line.name === 'string' &&
    typeof line.priceMinor === 'number' &&
    Number.isFinite(line.priceMinor) &&
    typeof line.currency === 'string' &&
    typeof line.quantity === 'number' &&
    Number.isFinite(line.quantity)
  )
}
