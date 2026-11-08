/**
 * Turns the price a person types ("50", "50.5", "1,200.75") into minor units (cents): 5000, 5050,
 * 120075. Returns null if the text isn't a price, so the form can say so.
 *
 * Why not Math.round(Number(text) * 100)? Decimal fractions like 0.29 have no exact binary form, so
 * 0.29 * 100 is 28.999999999999996. Reading the shillings and the cents as two whole numbers never
 * goes through a fraction at all. (formatPrice does the same in the other direction.)
 */
export function parseShillings(text: string): number | null {
  // Spaces around it and thousands separators are fine: " 1,200 " means 1200.
  const cleaned = text.trim().replaceAll(',', '');
  if (cleaned === '') {
    return null;
  }

  const parts = cleaned.split('.');
  if (parts.length > 2) {
    return null; // "1.2.3"
  }

  const shillingsText = parts[0];
  let centsText = '';
  if (parts.length === 2) {
    centsText = parts[1];
  }

  if (shillingsText === '' || !onlyDigits(shillingsText)) {
    return null; // ".50", "-5", "abc"
  }
  if (parts.length === 2) {
    if (centsText.length < 1 || centsText.length > 2 || !onlyDigits(centsText)) {
      return null; // "5.", "5.123", "5.ab"
    }
  }

  // "5" -> 0 cents, "5.5" -> 50 cents, "5.05" -> 5 cents.
  let cents = 0;
  if (centsText.length === 1) {
    cents = Number(centsText) * 10;
  } else if (centsText.length === 2) {
    cents = Number(centsText);
  }

  const shillings = Number(shillingsText);
  const total = shillings * 100 + cents;
  if (!Number.isSafeInteger(total)) {
    return null; // too big to be a real price
  }
  return total;
}

/** The text form of minor units for the input box: 5050 becomes "50.50" (no currency, no commas). */
export function shillingsText(priceMinor: number): string {
  const whole = Math.floor(priceMinor / 100);
  const cents = priceMinor % 100;
  let centsText = String(cents);
  if (cents < 10) {
    centsText = '0' + centsText;
  }
  return whole + '.' + centsText;
}

function onlyDigits(text: string): boolean {
  for (const character of text) {
    if (character < '0' || character > '9') {
      return false;
    }
  }
  return true;
}
