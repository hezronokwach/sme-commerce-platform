/**
 * A shop's web name is the last part of its address (/s/kamau-hardware). The backend accepts 3 to 50
 * lowercase letters, digits or hyphens; these helpers suggest one and check it before sending.
 */

export const WEB_NAME_MAX = 50;

/** "Kamau's Hardware & Paint" becomes "kamau-s-hardware-paint". */
export function webNameFrom(shopName: string): string {
  const lower = shopName.toLowerCase();
  let result = '';
  let lastWasHyphen = true; // so the result never starts with a hyphen
  for (const character of lower) {
    const isLetterOrDigit = (character >= 'a' && character <= 'z') || (character >= '0' && character <= '9');
    if (isLetterOrDigit) {
      result = result + character;
      lastWasHyphen = false;
    } else if (!lastWasHyphen) {
      result = result + '-';
      lastWasHyphen = true;
    }
  }
  if (result.length > WEB_NAME_MAX) {
    result = result.substring(0, WEB_NAME_MAX);
  }
  while (result.endsWith('-')) {
    result = result.substring(0, result.length - 1);
  }
  return result;
}

/** The same rule as the backend: ^[a-z0-9-]{3,50}$. */
export function isValidWebName(webName: string): boolean {
  return /^[a-z0-9-]{3,50}$/.test(webName);
}
