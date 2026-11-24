import { useState } from 'react'
import type { StoreProduct } from '../api/client'
import { ProductImage } from './ProductImage'
import { resizedPhoto, squarePhoto } from './photos'

// The product page's photos: one large photo, and small ones underneath to switch between them.
// A product with one photo (or none) shows just that, with no row of small photos.
export function ProductGallery({ product }: { product: StoreProduct }) {
  const [shown, setShown] = useState(0)
  const photos = product.imageUrls ?? []

  if (photos.length <= 1) {
    return <ProductImage product={product} />
  }

  let current = shown
  if (current >= photos.length) {
    current = 0
  }
  const name = product.name ?? ''

  return (
    <div className="product-gallery">
      <img
        src={resizedPhoto(photos[current], 1200)}
        alt={name + ', photo ' + (current + 1) + ' of ' + photos.length}
        className="product-image"
      />
      <ul className="gallery-thumbs">
        {photos.map((url, index) => (
          <li key={url}>
            <button
              type="button"
              aria-label={'Show photo ' + (index + 1)}
              aria-pressed={index === current}
              onClick={() => setShown(index)}
            >
              <img src={squarePhoto(url, 160)} alt="" />
            </button>
          </li>
        ))}
      </ul>
    </div>
  )
}
