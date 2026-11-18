package com.hezron.smecommerce.payments.internal.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.hezron.smecommerce.payments.internal.card.CardProperties;
import com.hezron.smecommerce.payments.internal.card.StripeClient;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Without Stripe keys, a card payment is refused with plain words: nothing crashes, nothing is sent. */
class CardNotSetUpTests {

    @Test
    void withoutKeysACardPaymentIsRefusedPolitely() {
        CardProperties noKeys = new CardProperties("", "", "", null, null);
        JsonMapper jsonMapper = JsonMapper.builder().build();
        CardPaymentProvider provider = new CardPaymentProvider(new StripeClient(noKeys, jsonMapper), jsonMapper);

        StartResult result = provider.start(new PaymentRequest(UUID.randomUUID(), 5000, "KES", "254712345670",
                "MM-000001"));

        assertThat(result.outcome()).isEqualTo(StartResult.Outcome.REJECTED);
        assertThat(result.reason()).isEqualTo("Card payments are not set up yet. Please choose another way to pay.");
    }

}
