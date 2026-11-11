// Money arrives from the backend as whole minor units (cents) plus a currency code, never as a
// decimal: 5000 + "KES" means KES 50.00. Only here, for showing it, is it turned into a decimal text.

// en-KE with the code ("KES"), not the symbol ("Ksh"), so it reads the same as on the M-Pesa prompt.
// Intl puts a no-break space after the code; we use a normal space so the text is easy to compare.
export function formatMoney(minor: number, currency: string): string {
  const formatter = new Intl.NumberFormat('en-KE', {
    style: 'currency',
    currency: currency,
    currencyDisplay: 'code',
  })
  const text = formatter.format(minor / 100)
  return text.replace(/ /g, ' ')
}
