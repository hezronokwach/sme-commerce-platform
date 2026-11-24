package com.hezron.smecommerce.payments.internal.provider;

import com.hezron.smecommerce.payments.PaymentProviderName;
import com.hezron.smecommerce.payments.internal.mpesa.DarajaClient;
import com.hezron.smecommerce.payments.internal.mpesa.MpesaProperties;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * M-Pesa through Daraja (Guide 5.4, 5.5, 5.7; ADR 0019). start() sends the PIN prompt; Safaricom calls
 * MpesaCallbackController with the result; queryStatus() asks when that call never comes.
 */
@Component
class MpesaPaymentProvider implements PaymentProvider {

    /** Daraja's answer to a query while the customer hasn't finished yet. */
    static final String STILL_PROCESSING = "500.001.1001";
    /** AccountReference may be at most 12 characters. */
    private static final int MAX_ACCOUNT_REFERENCE = 12;

    private final DarajaClient daraja;
    private final JsonMapper jsonMapper;

    MpesaPaymentProvider(DarajaClient daraja, JsonMapper jsonMapper) {
        this.daraja = daraja;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public PaymentProviderName name() {
        return PaymentProviderName.MPESA;
    }

    @Override
    public StartResult start(PaymentRequest request) {
        if (!daraja.isConfigured()) {
            return StartResult.rejected("M-Pesa payments are not set up yet. Please choose another way to pay.");
        }
        if (request.amountMinor() % 100 != 0) {
            return StartResult.rejected("M-Pesa only takes whole shillings.");
        }
        MpesaProperties settings = daraja.properties();
        String timestamp = daraja.timestamp();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("BusinessShortCode", settings.shortcode());
        body.put("Password", daraja.password(timestamp));
        body.put("Timestamp", timestamp);
        body.put("TransactionType", settings.transactionType());
        body.put("Amount", request.amountMinor() / 100);
        body.put("PartyA", request.payerPhone());
        body.put("PartyB", settings.shortcode());
        body.put("PhoneNumber", request.payerPhone());
        body.put("CallBackURL", settings.callbackUrl());
        body.put("AccountReference", accountReference(request.orderNumber()));
        body.put("TransactionDesc", "Order " + request.orderNumber());

        JsonNode answer = daraja.stkPush(body);
        if ("0".equals(answer.path("ResponseCode").asString(""))) {
            return StartResult.accepted(answer.path("CheckoutRequestID").asString());
        }
        return StartResult.rejected(messageOf(answer));
        // Timeouts and connection errors fly on: the caller records UNKNOWN, never FAILED.
    }

    @Override
    public StatusResult queryStatus(String providerReference) {
        String timestamp = daraja.timestamp();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("BusinessShortCode", daraja.properties().shortcode());
        body.put("Password", daraja.password(timestamp));
        body.put("Timestamp", timestamp);
        body.put("CheckoutRequestID", providerReference);

        JsonNode answer = daraja.stkQuery(body);
        String resultCode = answer.path("ResultCode").asString("");
        if ("0".equals(resultCode)) {
            // The query states no amount: the confirmed reference is the proof (ADR 0014).
            return new StatusResult(StatusResult.Outcome.SUCCEEDED, providerReference, "0", null);
        }
        if (!resultCode.isEmpty()) {
            return new StatusResult(StatusResult.Outcome.FAILED, null, resultCode, null);
        }
        if (STILL_PROCESSING.equals(answer.path("errorCode").asString(""))) {
            return new StatusResult(StatusResult.Outcome.PENDING, null, null, null);
        }
        // Anything else (bad credentials, a bad request) is a real problem: reconciliation asks again later.
        throw new IllegalStateException("Daraja refused the status query: " + answer.path("errorCode").asString("?"));
    }

    /**
     * Reads Safaricom's callback (Body.stkCallback). Success carries CallbackMetadata with the amount
     * and the receipt; a failure only has its ResultCode and ResultDesc.
     */
    @Override
    public Optional<CallbackResult> parseCallback(String rawBody) {
        JsonNode callback;
        try {
            callback = jsonMapper.readTree(rawBody).path("Body").path("stkCallback");
        } catch (JacksonException notJson) {
            return Optional.empty();
        }
        String reference = callback.path("CheckoutRequestID").asString("");
        String resultCode = callback.path("ResultCode").asString("");
        if (reference.isBlank() || resultCode.isBlank()) {
            return Optional.empty();
        }

        Long amountMinor = null;
        String receipt = null;
        for (JsonNode item : callback.path("CallbackMetadata").path("Item")) {
            String name = item.path("Name").asString("");
            if ("Amount".equals(name)) {
                BigDecimal shillings = item.path("Value").decimalValue();
                amountMinor = shillings.movePointRight(2).longValue();
            } else if ("MpesaReceiptNumber".equals(name)) {
                receipt = item.path("Value").asString();
            }
        }
        return Optional.of(new CallbackResult(reference, resultCode, receipt, amountMinor,
                callback.path("ResultDesc").asString("")));
    }

    private static String accountReference(String orderNumber) {
        String compact = orderNumber.replace("-", "");
        if (compact.length() > MAX_ACCOUNT_REFERENCE) {
            return compact.substring(compact.length() - MAX_ACCOUNT_REFERENCE);
        }
        return compact;
    }

    private static String messageOf(JsonNode answer) {
        String message = answer.path("ResponseDescription").asString("");
        if (message.isBlank()) {
            message = answer.path("errorMessage").asString("");
        }
        if (message.isBlank()) {
            message = "M-Pesa couldn't start the payment. Please try again.";
        }
        return message;
    }

}
