// The same rules as the backend's PhoneNumber.parse, so the customer hears about a typo before the
// order is sent. The backend still checks again: the browser's check is a convenience, not a guarantee.
//
//   0712 345 678   +254 712 345 678   254712345678   712345678   (0712) 345-678
//   all become 254712345678 (a Kenyan mobile: 12 digits starting with 2547)

const COUNTRY_CODE = '254'
const LENGTH = 12

// Returns the number as 2547XXXXXXXX, or null when it isn't a Kenyan mobile number.
export function normalisePhone(typed: string): string | null {
  let digits = ''
  for (const character of typed) {
    if (character >= '0' && character <= '9') {
      digits = digits + character
    } else if (character !== ' ' && character !== '+' && character !== '-' && character !== '(' && character !== ')') {
      // Letters or other symbols: not a phone number.
      return null
    }
  }

  let number: string
  if (digits.startsWith(COUNTRY_CODE)) {
    number = digits
  } else if (digits.startsWith('0')) {
    number = COUNTRY_CODE + digits.substring(1)
  } else {
    number = COUNTRY_CODE + digits
  }

  if (number.length !== LENGTH || !number.startsWith('2547')) {
    return null
  }
  return number
}
