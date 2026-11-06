package com.hezron.smecommerce.ordering.internal.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Plain unit tests for the prefix rule. In the same package as OrderNumberService, because
 * prefixFromSlug is package-private (only the service and its tests use it).
 */
class OrderNumberPrefixTests {

    @Test
    void firstLetterOfEachWord() {
        assertThat(OrderNumberService.prefixFromSlug("mama-mboga")).isEqualTo("MM");
        assertThat(OrderNumberService.prefixFromSlug("duka-la-mama-njeri")).isEqualTo("DLMN");
    }

    @Test
    void oneWordGivesItsFirstTwoLetters() {
        assertThat(OrderNumberService.prefixFromSlug("kamau")).isEqualTo("KA");
    }

    @Test
    void digitsAreKept() {
        assertThat(OrderNumberService.prefixFromSlug("shop-24-7")).isEqualTo("S27");
    }

    @Test
    void neverLongerThanSix() {
        assertThat(OrderNumberService.prefixFromSlug("a-b-c-d-e-f-g-h")).isEqualTo("ABCDEF");
    }

}
