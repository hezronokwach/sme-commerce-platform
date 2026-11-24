import type { StoreProduct } from '../api/client'
import { resizedPhoto } from './photos'

// The product's main photo, or a plain box with the first letter when there is none.
export function ProductImage({ product }: { product: StoreProduct }) {
  if (product.imageUrl) {
    return (
      <img
        src={resizedPhoto(product.imageUrl, 800)}
        alt={product.name ?? ''}
        className="product-image"
        loading="lazy"
      />
    )
  }
  const firstLetter = (product.name ?? '?').charAt(0).toUpperCase()
  return (
    <span className="product-image product-image-empty" aria-hidden="true">
      {firstLetter}
    </span>
  )
}
