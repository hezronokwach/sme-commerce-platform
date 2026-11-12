// The private order links this browser has, so the customer can come back to their order page.
//
// The token proves an order is theirs, so it never goes in a web address (ADR 0010): addresses end up
// in server logs, proxy logs and browser history. It is kept here, in this browser only, under
// orders:<shop slug>. The catch: another phone or computer can't open the order. Sending the link by
// SMS is a later feature.

export type OrderLink = {
  orderNumber: string
  accessToken: string
}

// Enough for a customer's recent orders; the oldest are forgotten first.
const KEEP_AT_MOST = 20

function storageKey(shopSlug: string): string {
  return 'orders:' + shopSlug.toLowerCase()
}

export function saveOrderLink(shopSlug: string, link: OrderLink): void {
  const links = loadOrderLinks(shopSlug)
  const kept: OrderLink[] = [link]
  for (const existing of links) {
    if (existing.orderNumber !== link.orderNumber && kept.length < KEEP_AT_MOST) {
      kept.push(existing)
    }
  }
  try {
    window.localStorage.setItem(storageKey(shopSlug), JSON.stringify(kept))
  } catch {
    // Not remembered: the order page still works now, from the token passed to it in memory.
  }
}

// The token for one order, or null if this browser doesn't have it.
export function findOrderToken(shopSlug: string, orderNumber: string): string | null {
  for (const link of loadOrderLinks(shopSlug)) {
    if (link.orderNumber === orderNumber) {
      return link.accessToken
    }
  }
  return null
}

function loadOrderLinks(shopSlug: string): OrderLink[] {
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

  const links: OrderLink[] = []
  for (const item of parsed) {
    if (typeof item === 'object' && item !== null) {
      const link = item as Record<string, unknown>
      if (typeof link.orderNumber === 'string' && typeof link.accessToken === 'string') {
        links.push({ orderNumber: link.orderNumber, accessToken: link.accessToken })
      }
    }
  }
  return links
}
