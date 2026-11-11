import type { StoreProduct } from '../api/client'

// The owner's photo, or a plain box with the first letter when there is none.
export function ProductImage({ product }: { product: StoreProduct }) {
  if (product.imageUrl) {
    return <img src={product.imageUrl} alt={product.name ?? ''} className="product-image" />
  }
  const firstLetter = (product.name ?? '?').charAt(0).toUpperCase()
  return (
    <span className="product-image product-image-empty" aria-hidden="true">
      {firstLetter}
    </span>
  )
}
