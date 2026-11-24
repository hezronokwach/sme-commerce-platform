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

}
