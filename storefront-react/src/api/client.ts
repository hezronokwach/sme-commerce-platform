// The one place the storefront talks to the backend.
//
// schema.d.ts is generated from docs/api/openapi.json by `npm run generate-api` (never edit it by hand).
// It holds only types: every path, its parameters and what it returns. openapi-fetch is a thin wrapper
// around the browser's fetch that reads those types, so a wrong path, a missing parameter or a field
// the backend renamed fails the build instead of failing in a customer's browser.
import createClient from 'openapi-fetch'
import type { components, paths } from './schema'

// Where the backend is. In development it's this page's own address, and Vite's proxy passes '/api'
// on to the backend (vite.config.ts). A deployed build sets VITE_API_BASE_URL to the backend's address.
// The address must be absolute: fetch's Request refuses a bare '/api/...' outside a browser (tests).
function backendAddress(): string {
  const configured = import.meta.env.VITE_API_BASE_URL
  if (configured) {
    return configured
  }
  return window.location.origin
}

// Looks up fetch at each call instead of once at start-up, so tests can put a stand-in fetch in place.
function callFetch(request: Request): Promise<Response> {
  return globalThis.fetch(request)
}

export const api = createClient<paths>({ baseUrl: backendAddress(), fetch: callFetch })

// Short names for the shapes the pages use.
export type ShopProfile = components['schemas']['ShopProfileResponse']
export type StoreProduct = components['schemas']['StorefrontProductResponse']
export type StoreCategory = components['schemas']['CategoryResponse']
