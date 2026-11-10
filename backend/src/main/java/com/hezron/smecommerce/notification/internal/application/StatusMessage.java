package com.hezron.smecommerce.notification.internal.application;

/**
 * One "status" message on the live stream, sent as JSON:
 * <pre>{"orderNumber": "MM-000123", "status": "PAID", "message": "Payment received. Thank you!", "last": true}</pre>
 *
 * @param status  the order's status as text, for the page's code
 * @param message words for the customer
 * @param last    true when nothing more will happen to this order's payment: the stream closes after it
 */
public record StatusMessage(String orderNumber, String status, String message, boolean last) {
}
