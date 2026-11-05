package com.hezron.smecommerce.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.hezron.smecommerce.catalog.internal.domain.Slugs;
import org.junit.jupiter.api.Test;

/** Plain unit tests: no Spring, no database. */
class SlugsTests {

    @Test
    void turnsANameIntoAWebName() {
        assertThat(Slugs.fromName("Fresh Vegetables & Fruit", 80)).isEqualTo("fresh-vegetables-fruit");
    }

    @Test
    void removesAccents() {
        assertThat(Slugs.fromName("Café Latte", 80)).isEqualTo("cafe-latte");
    }

    @Test
    void neverStartsOrEndsWithAHyphen() {
        assertThat(Slugs.fromName("  ** Sukuma Wiki! ", 80)).isEqualTo("sukuma-wiki");
    }

    @Test
    void isCutToTheMaximumLengthWithoutATrailingHyphen() {
        // "abc-def" cut to 4 characters would be "abc-": the hyphen is removed.
        assertThat(Slugs.fromName("abc def", 4)).isEqualTo("abc");
    }

    @Test
    void nameWithoutLettersOrDigitsGivesAnEmptySlug() {
        assertThat(Slugs.fromName("!!!", 80)).isEmpty();
    }

    @Test
    void checksTheSameFormatAsTheDatabase() {
        assertThat(Slugs.isValid("fresh-vegetables")).isTrue();
        assertThat(Slugs.isValid("mandazi2")).isTrue();

        assertThat(Slugs.isValid("")).isFalse();
        assertThat(Slugs.isValid("-start")).isFalse();
        assertThat(Slugs.isValid("end-")).isFalse();
        assertThat(Slugs.isValid("double--hyphen")).isFalse();
        assertThat(Slugs.isValid("Upper")).isFalse();
        assertThat(Slugs.isValid("with space")).isFalse();
    }

}
