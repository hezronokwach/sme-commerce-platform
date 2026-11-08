package com.hezron.smecommerce.payments.internal.provider;

/**
 * A provider's callback, read into the same shape for every provider.
 * <p>
 * The result codes follow M-Pesa's, because that is the main provider: "0" means success, anything
 * else is a failure (see PaymentResultCodes for what each one means to the customer).
 *
 * @param providerReference which payment this is about
 * @param resultCode        "0" for success
 * @param receiptNumber     only on success
 * @param amountMinor       the amount the provider says it collected, in cents; null if it didn't say
 * @param resultDescription the provider's own words, kept for failures we don't know
 */
public record CallbackResult(String providerReference, String resultCode, String receiptNumber,
        Long amountMinor, String resultDescription) {

    public boolean isSuccess() {
        return "0".equals(resultCode);
    }

}
