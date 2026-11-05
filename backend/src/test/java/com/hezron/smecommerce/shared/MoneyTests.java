package com.hezron.smecommerce.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Plain unit tests: no Spring, no database. */
class MoneyTests {

    @Test
    void holdsWholeMinorUnits() {
        Money fiftyShillings = Money.of(5000, "KES");

        assertThat(fiftyShillings.amountMinor()).isEqualTo(5000);
        assertThat(fiftyShillings.currency()).isEqualTo("KES");
    }

    @Test
    void twoEqualAmountsAreEqual() {
        // Records compare their fields, so prices can be compared with equals.
        assertThat(Money.of(5000, "KES")).isEqualTo(Money.of(5000, "KES"));
        assertThat(Money.of(5000, "KES")).isNotEqualTo(Money.of(5000, "USD"));
    }

    @Test
    void refusesACurrencyThatIsNotAThreeLetterCode() {
        assertThatThrownBy(() -> Money.of(5000, "kes")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.of(5000, "SHILLING")).isInstanceOf(IllegalArgumentException.class);
    }

}
