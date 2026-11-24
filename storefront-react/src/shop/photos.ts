// Product photos live on Cloudinary (ADR 0022). Cloudinary makes a smaller copy on the fly when the
// address asks for it, so a phone never downloads a 4000-pixel original:
//   .../image/upload/v1/shop/hammer.jpg  becomes  .../image/upload/c_limit,w_800,f_auto,q_auto/v1/shop/hammer.jpg
// c_limit shrinks without cropping or enlarging; f_auto and q_auto pick a light format and quality.
// Any other address is returned unchanged.

const CLOUDINARY = 'https://res.cloudinary.com/'
const MARKER = '/image/upload/'

export function resizedPhoto(url: string, width: number): string {
  return withTransformation(url, 'c_limit,w_' + width + ',f_auto,q_auto')
}

/** A square crop, for thumbnails. */
export function squarePhoto(url: string, size: number): string {
  return withTransformation(url, 'c_fill,w_' + size + ',h_' + size + ',f_auto,q_auto')
}

function withTransformation(url: string, transformation: string): string {
  const at = url.indexOf(MARKER)
  if (!url.startsWith(CLOUDINARY) || at === -1) {
    return url
  }
  const start = url.substring(0, at + MARKER.length)
  const rest = url.substring(at + MARKER.length)
  return start + transformation + '/' + rest
}
