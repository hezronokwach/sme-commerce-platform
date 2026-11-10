package com.hezron.smecommerce.payments;

/**
 * Where an order's newest payment attempt is.
 *
 * @param status        plain text: INITIATED, PENDING, SUCCEEDED, FAILED, TIMED_OUT or UNKNOWN
 * @param failureReason words for the customer, only for FAILED
 */
public record PaymentStatusView(String status, String failureReason) {
}
