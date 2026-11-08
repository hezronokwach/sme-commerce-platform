/**
 * Reads the "role" claim (OWNER or STAFF) out of an access token.
 *
 * A JWT is three parts joined by dots: header.payload.signature. The payload is plain JSON, only
 * base64url-encoded, so anyone holding the token can read it (it is signed, not secret). The browser
 * reads it only to decide which menu items to show; the backend checks the signature and the role on
 * every request, so editing the token here would gain nothing.
 *
 * Returns null if the token can't be read.
 */
export function roleFromToken(token: string): string | null {
  const parts = token.split('.');
  if (parts.length !== 3) {
    return null;
  }

  try {
    const payloadJson = decodeBase64Url(parts[1]);
    const payload = JSON.parse(payloadJson);
    if (typeof payload.role === 'string') {
      return payload.role;
    }
    return null;
  } catch {
    // Not base64, or not JSON: not one of our tokens.
    return null;
  }
}

/**
 * base64url is base64 with "-" and "_" instead of "+" and "/", and without the "=" padding at the
 * end. atob() only understands plain base64, so we turn it back first.
 */
function decodeBase64Url(text: string): string {
  let base64 = text.replaceAll('-', '+').replaceAll('_', '/');
  while (base64.length % 4 !== 0) {
    base64 = base64 + '=';
  }
  return atob(base64);
}
