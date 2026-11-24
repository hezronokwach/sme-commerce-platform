import { describe, expect, it } from 'vitest'
import { resizedPhoto, squarePhoto } from './photos'

const PHOTO = 'https://res.cloudinary.com/demo/image/upload/v1/sme-commerce/shop/hammer.jpg'

describe('photo addresses', () => {
  it('ask Cloudinary for a smaller copy', () => {
    expect(resizedPhoto(PHOTO, 800)).toBe(
      'https://res.cloudinary.com/demo/image/upload/c_limit,w_800,f_auto,q_auto/v1/sme-commerce/shop/hammer.jpg',
    )
    expect(squarePhoto(PHOTO, 120)).toBe(
      'https://res.cloudinary.com/demo/image/upload/c_fill,w_120,h_120,f_auto,q_auto/v1/sme-commerce/shop/hammer.jpg',
    )
  })

  it('leave other addresses alone', () => {
    expect(resizedPhoto('https://example.com/a.jpg', 800)).toBe('https://example.com/a.jpg')
  })
})
