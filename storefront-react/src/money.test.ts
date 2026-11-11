import { describe, expect, it } from 'vitest'
import { formatMoney } from './money'

describe('formatMoney', () => {
  it('turns minor units into shillings and cents', () => {
    expect(formatMoney(5000, 'KES')).toBe('KES 50.00')
  })

  it('keeps the cents', () => {
    expect(formatMoney(12345, 'KES')).toBe('KES 123.45')
  })

  it('groups thousands', () => {
    expect(formatMoney(150000000, 'KES')).toBe('KES 1,500,000.00')
  })

  it('shows zero', () => {
    expect(formatMoney(0, 'KES')).toBe('KES 0.00')
  })
})
