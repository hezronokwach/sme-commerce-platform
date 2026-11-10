package com.hezron.smecommerce.notification.internal.application;

/**
 * The words a customer reads for each order status. Kept in one place so the first message (when the
 * stream opens) and the later ones (from events) always say the same thing.
 */
final class StatusMessages {

    private StatusMessages() {
    }

    /** After these the payment story is over, so the stream closes. */
    static boolean isLast(String orderStatus) {
        if ("PAID".equals(orderStatus)) {
            return true;
        }
        if ("CANCELLED".equals(orderStatus)) {
            return true;
        }
        if ("EXPIRED".equals(orderStatus)) {
            return true;
        }
        if ("FULFILLED".equals(orderStatus)) {
            return true;
        }
        return false;
    }

    /**
     * Words for the order's status. While waiting for payment the payment's own status decides:
     * UNKNOWN is shown as "checking", never as "failed" (CLAUDE.md: unknown is a real state).
     *
     * @param paymentStatus        newest attempt's status, or null if there is none yet
     * @param paymentFailureReason words from payments, only when that attempt FAILED
     */
    static String forStatus(String orderStatus, String paymentStatus, String paymentFailureReason) {
        if ("PENDING_PAYMENT".equals(orderStatus)) {
            return whileWaitingForPayment(paymentStatus, paymentFailureReason);
        }
        if ("CREATED".equals(orderStatus)) {
            return "Order received.";
        }
        if ("PAID".equals(orderStatus)) {
            return "Payment received. Thank you!";
        }
        if ("CANCELLED".equals(orderStatus)) {
            return "This order was cancelled.";
        }
        if ("EXPIRED".equals(orderStatus)) {
            return "The time to pay ran out, so this order was cancelled.";
        }
        if ("FULFILLED".equals(orderStatus)) {
            return "Your order has been handed over. Enjoy!";
        }
        return "Order status: " + orderStatus;
    }

    /** A retryable failure: the order is still waiting, and the customer can pay again. */
    static String paymentFailed(String reason) {
        return "Payment failed: " + reason + " You can try again.";
    }

    private static String whileWaitingForPayment(String paymentStatus, String paymentFailureReason) {
        if ("FAILED".equals(paymentStatus) && paymentFailureReason != null) {
            return paymentFailed(paymentFailureReason);
        }
        if ("UNKNOWN".equals(paymentStatus) || "TIMED_OUT".equals(paymentStatus)) {
            return "We are checking your payment. Please wait a moment.";
        }
        return "Waiting for payment. Check your phone and enter your PIN.";
    }

}
