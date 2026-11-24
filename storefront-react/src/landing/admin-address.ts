// Where the shop admin lives, for the landing page's "Open your shop" and "Log in" links.
// The admin is its own site (another Vercel project), so this must be a full address.
//
// VITE_ADMIN_URL wins when it is set. Otherwise: the Angular dev server in development, and the
// deployed admin in a production build (the same address docs/deploy.md uses).
export const DEPLOYED_ADMIN = 'https://sme-commerce-platform.vercel.app'
export const LOCAL_ADMIN = 'http://localhost:4200'

export function adminAddress(): string {
  const configured = import.meta.env.VITE_ADMIN_URL
  if (configured) {
    return configured
  }
  if (import.meta.env.DEV) {
    return LOCAL_ADMIN
  }
  return DEPLOYED_ADMIN
}
