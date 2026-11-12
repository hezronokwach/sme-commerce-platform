import { describe, expect, it } from 'vitest'
import { normalisePhone } from './phone'

// The same examples as the backend's PhoneNumber rules.
describe('normalisePhone', () => {
  it('accepts the ways Kenyans write a mobile number', () => {
    expect(normalisePhone('0712 345 678')).toBe('254712345678')
    expect(normalisePhone('+254 712 345 678')).toBe('254712345678')
    expect(normalisePhone('254712345678')).toBe('254712345678')
    expect(normalisePhone('712345678')).toBe('254712345678')
    expect(normalisePhone('(0712) 345-678')).toBe('254712345678')
  })

  it('refuses letters, wrong lengths and numbers that are not 07 mobiles', () => {
    expect(normalisePhone('0712 34S 678')).toBeNull()
    expect(normalisePhone('0712 345 67')).toBeNull()
    expect(normalisePhone('0712 345 6789')).toBeNull()
    expect(normalisePhone('020 123 4567')).toBeNull()
    expect(normalisePhone('')).toBeNull()
  })
})
