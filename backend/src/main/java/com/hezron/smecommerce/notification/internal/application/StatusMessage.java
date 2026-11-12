package com.hezron.smecommerce.notification.internal.application;

/**
 * One "status" message on the live stream, sent as JSON:
 * <pre>{"orderNumber": "MM-000123", "status": "PENDING_PAYMENT", "paymentStatus": "FAILED",
 *  "message": "Payment failed: You cancelled the payment on your phone. You can try again.", "last": false}</pre>
 *
 * @param status        the order's status as text, for the page's code
 * @param paymentStatus the newest payment attempt's status (INITIATED, PENDING, SUCCEEDED, FAILED,
 *                      TIMED_OUT, UNKNOWN), or null if none was made yet. The page decides what to show
 *                      from status and paymentStatus, never from the words in message.
 * @param message       words for the customer
 * @param last          true when nothing more will happen to this order's payment: the stream closes after it
 */
public record StatusMessage(String orderNumber, String status, String paymentStatus, String message,
        boolean last) {
}
