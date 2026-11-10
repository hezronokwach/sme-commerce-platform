package com.hezron.smecommerce.ordering.internal.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The private order-link token: only its hash is stored, and only the right token matches it. */
class OrderAccessTokensTests {

    @Test
    void theTokenMatchesItsOwnHashOnly() {
        String token = OrderAccessTokens.newToken();
        String storedHash = OrderAccessTokens.hash(token);

        assertThat(OrderAccessTokens.matches(token, storedHash)).isTrue();
        assertThat(OrderAccessTokens.matches(OrderAccessTokens.newToken(), storedHash)).isFalse();
        assertThat(OrderAccessTokens.matches("", storedHash)).isFalse();
    }

    @Test
    void theStoredHashIsNotTheToken() {
        String token = OrderAccessTokens.newToken();

        assertThat(OrderAccessTokens.hash(token)).hasSize(64).isNotEqualTo(token);
    }

}
