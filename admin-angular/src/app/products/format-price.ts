/**
 * Shows a price stored in minor units (cents) the way people read it: 5000 with "KES" becomes
 * "KES 50.00". The backend never sends decimals for money (see the Money type); only the screen does.
 *
 * Uses whole-number maths (division and remainder by 100) instead of priceMinor / 100, so no
 * floating-point rounding can sneak in.
 */
export function formatPrice(priceMinor: number | undefined, currency: string | undefined): string {
  if (priceMinor === undefined) {
    return '';
  }

  let sign = '';
  let amount = priceMinor;
  if (amount < 0) {
    sign = '-';
    amount = -amount;
  }

  const wholeUnits = Math.floor(amount / 100);
  const cents = amount % 100;
  let centsText = String(cents);
  if (cents < 10) {
    centsText = '0' + centsText;
  }

  let text = sign + addThousandsSeparators(wholeUnits) + '.' + centsText;
  if (currency !== undefined) {
    text = currency + ' ' + text;
  }
  return text;
}

/** 1500000 becomes "1,500,000". */
function addThousandsSeparators(wholeUnits: number): string {
  const digits = String(wholeUnits);
  let result = '';
  let digitsInGroup = 0;
  for (let i = digits.length - 1; i >= 0; i--) {
    if (digitsInGroup === 3) {
      result = ',' + result;
      digitsInGroup = 0;
    }
    result = digits[i] + result;
    digitsInGroup++;
  }
  return result;
}
