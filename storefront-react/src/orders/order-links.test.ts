import { afterEach, describe, expect, it, vi } from 'vitest'
import { findOrderToken, saveOrderLink } from './order-links'

describe('order links', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('finds the token of a saved order, per shop', () => {
    saveOrderLink('mamamboga', { orderNumber: 'MM-000001', accessToken: 'secret-1' })
    saveOrderLink('kamau', { orderNumber: 'KH-000001', accessToken: 'secret-2' })

    expect(findOrderToken('mamamboga', 'MM-000001')).toBe('secret-1')
    expect(findOrderToken('mamamboga', 'KH-000001')).toBeNull()
    expect(findOrderToken('kamau', 'KH-000001')).toBe('secret-2')
  })

  it('keeps only the most recent orders', () => {
    for (let i = 1; i <= 25; i++) {
      saveOrderLink('mamamboga', { orderNumber: 'MM-' + i, accessToken: 'token-' + i })
    }

    expect(findOrderToken('mamamboga', 'MM-25')).toBe('token-25')
    expect(findOrderToken('mamamboga', 'MM-6')).toBe('token-6')
    expect(findOrderToken('mamamboga', 'MM-5')).toBeNull()
  })

  it('gives nothing back, and does not throw, when storage is blocked', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('SecurityError')
    })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('SecurityError')
    })

    expect(() => saveOrderLink('mamamboga', { orderNumber: 'MM-1', accessToken: 't' })).not.toThrow()
    expect(findOrderToken('mamamboga', 'MM-1')).toBeNull()
  })
})
