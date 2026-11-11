// Any address the storefront doesn't know, for example the bare site root: a shop's address always
// starts with /s/ and the shop's web name, which the shop owner shares.
// No <main> of its own: inside a shop, ShopLayout already gives one.
export function NotFoundPage() {
  return (
    <>
      <h1>Page not found</h1>
      <p>Check the link the shop sent you. A shop's address looks like /s/shop-name.</p>
    </>
  )
}
