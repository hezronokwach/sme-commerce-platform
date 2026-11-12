import { describe, expect, it } from 'vitest'
import { isFinal, viewFor } from './order-view'

describe('what the order page shows', () => {
  it('follows the table in the plan', () => {
    expect(viewFor('PENDING_PAYMENT', null)).toBe('waiting')
    expect(viewFor('PENDING_PAYMENT', 'INITIATED')).toBe('waiting')
    expect(viewFor('PENDING_PAYMENT', 'PENDING')).toBe('waiting')
    expect(viewFor('PENDING_PAYMENT', 'UNKNOWN')).toBe('checking')
    expect(viewFor('PENDING_PAYMENT', 'TIMED_OUT')).toBe('checking')
    expect(viewFor('PENDING_PAYMENT', 'FAILED')).toBe('failed')
    expect(viewFor('PAID', 'SUCCEEDED')).toBe('paid')
    expect(viewFor('FULFILLED', 'SUCCEEDED')).toBe('paid')
    expect(viewFor('CANCELLED', 'FAILED')).toBe('cancelled')
    expect(viewFor('EXPIRED', 'PENDING')).toBe('expired')
  })

  it('never says "failed" unless the server said the payment FAILED', () => {
    const orderStatuses = ['CREATED', 'PENDING_PAYMENT', 'PAID', 'FULFILLED', 'CANCELLED', 'EXPIRED', 'SOMETHING_NEW', null]
    const paymentStatuses = ['INITIATED', 'PENDING', 'SUCCEEDED', 'TIMED_OUT', 'UNKNOWN', 'SOMETHING_NEW', null]
    for (const orderStatus of orderStatuses) {
      for (const paymentStatus of paymentStatuses) {
        expect(viewFor(orderStatus, paymentStatus)).not.toBe('failed')
      }
    }
  })

  it('stops watching only after paid, cancelled or expired', () => {
    expect(isFinal('paid')).toBe(true)
    expect(isFinal('cancelled')).toBe(true)
    expect(isFinal('expired')).toBe(true)
    expect(isFinal('waiting')).toBe(false)
    expect(isFinal('checking')).toBe(false)
    expect(isFinal('failed')).toBe(false)
  })
})
