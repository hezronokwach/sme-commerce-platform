package com.hezron.smecommerce.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Plain unit tests: no Spring, no database. */
class PhoneNumberTests {

    @Test
    void everyCommonWayOfWritingANumberGivesTheStoredForm() {
        assertThat(PhoneNumber.parse("0712345678").value()).isEqualTo("254712345678");
        assertThat(PhoneNumber.parse("0712 345 678").value()).isEqualTo("254712345678");
        assertThat(PhoneNumber.parse("+254712345678").value()).isEqualTo("254712345678");
        assertThat(PhoneNumber.parse("+254 712 345 678").value()).isEqualTo("254712345678");
        assertThat(PhoneNumber.parse("254712345678").value()).isEqualTo("254712345678");
        assertThat(PhoneNumber.parse("712345678").value()).isEqualTo("254712345678");
        assertThat(PhoneNumber.parse("(0712) 345-678").value()).isEqualTo("254712345678");
    }

    @Test
    void twoWaysOfWritingTheSameNumberAreEqual() {
        assertThat(PhoneNumber.parse("0712 345 678")).isEqualTo(PhoneNumber.parse("+254712345678"));
    }

    @Test
    void anythingElseIsRefused() {
        assertThatThrownBy(() -> PhoneNumber.parse(null)).isInstanceOf(InvalidPhoneNumberException.class);
        assertThatThrownBy(() -> PhoneNumber.parse("")).isInstanceOf(InvalidPhoneNumberException.class);
        // Too short and too long.
        assertThatThrownBy(() -> PhoneNumber.parse("071234567")).isInstanceOf(InvalidPhoneNumberException.class);
        assertThatThrownBy(() -> PhoneNumber.parse("07123456789")).isInstanceOf(InvalidPhoneNumberException.class);
        // A landline (020...) and another country (+255...).
        assertThatThrownBy(() -> PhoneNumber.parse("0201234567")).isInstanceOf(InvalidPhoneNumberException.class);
        assertThatThrownBy(() -> PhoneNumber.parse("+255712345678")).isInstanceOf(InvalidPhoneNumberException.class);
        // Letters.
        assertThatThrownBy(() -> PhoneNumber.parse("0712abc678")).isInstanceOf(InvalidPhoneNumberException.class);
    }

    @Test
    void toStringHidesTheMiddleSoLogsNeverHoldAFullNumber() {
        PhoneNumber phone = PhoneNumber.parse("0712345678");

        assertThat(phone.toString()).isEqualTo("2547******78");
        assertThat(phone.toString()).doesNotContain("12345");
    }

}
