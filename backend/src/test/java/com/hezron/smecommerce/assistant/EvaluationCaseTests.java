package com.hezron.smecommerce.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The scoring rules and the question file. The real score comes from AssistantEvaluationTests. */
class EvaluationCaseTests {

    @Test
    void theFileHasFifteenQuestionsForTheTwoShops() throws IOException {
        List<EvaluationCase> cases = EvaluationCase.load();

        assertThat(cases).hasSize(15);
        for (EvaluationCase row : cases) {
            assertThat(row.shop()).isIn("mamamboga", "kamauhardware");
            assertThat(row.mustContain()).isNotEmpty();
        }
    }

    @Test
    void anAnswerNeedsOneExpectedOptionAndNoForbiddenText() {
        EvaluationCase row = new EvaluationCase("Do you sell cement?", "don't/do not", "750", "mamamboga");

        assertThat(row.passes("Sorry, I DON'T know.")).isTrue();
        assertThat(row.passes("We do not stock it.")).isTrue();
        assertThat(row.passes("Cement costs 750.")).isFalse();
        assertThat(row.passes("I don't sell it, but Kamau charges 750.")).isFalse();
    }

    @Test
    void spacingAndTypographicPunctuationDontMatter() {
        EvaluationCase sundays = new EvaluationCase("Do you deliver on Sundays?", "9am", "", "mamamboga");
        EvaluationCase cement = new EvaluationCase("Do you sell cement?", "don't", "750", "mamamboga");
        EvaluationCase payment = new EvaluationCase("Which payment methods?", "M-Pesa", "", "mamamboga");

        assertThat(sundays.passes("Yes, between 9 am and 1 pm.")).isTrue();
        assertThat(sundays.passes("Yes, between 9 am and 1 pm.")).isTrue();
        assertThat(cement.passes("We don’t sell cement.")).isTrue();
        assertThat(payment.passes("We accept M‑Pesa.")).isTrue();
        assertThat(payment.passes("We accept M‑Pay​sa.")).isFalse();
    }

    @Test
    void theCantAnswerRightNowReplyNeverPasses() {
        EvaluationCase row = new EvaluationCase("Can I return cut vegetables?", "no/not/cannot/can't", "", "mamamboga");

        assertThat(row.passes(EvaluationCase.UNAVAILABLE)).isFalse();
        assertThat(row.passes("No, cut vegetables cannot be returned.")).isTrue();
    }

}
