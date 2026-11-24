package com.hezron.smecommerce.assistant;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One row of assistant-eval.csv (Guide 7.3). An answer passes if it contains one of the "/"-separated
 * must_contain options and none of must_not_contain. Fields hold no commas, so a plain split is enough.
 */
record EvaluationCase(String question, String mustContain, String mustNotContain, String shop) {

    static List<EvaluationCase> load() throws IOException {
        List<EvaluationCase> cases = new ArrayList<>();
        try (InputStream file = EvaluationCase.class.getResourceAsStream("/assistant-eval.csv");
                BufferedReader lines = new BufferedReader(new InputStreamReader(file, StandardCharsets.UTF_8))) {
            lines.readLine(); // the header
            String line = lines.readLine();
            while (line != null) {
                if (!line.isBlank()) {
                    String[] fields = line.split(",", -1);
                    cases.add(new EvaluationCase(fields[0], fields[1], fields[2], fields[3]));
                }
                line = lines.readLine();
            }
        }
        return cases;
    }

    boolean passes(String answer) {
        String lower = answer.toLowerCase(Locale.ROOT);
        if (!mustNotContain.isEmpty() && lower.contains(mustNotContain.toLowerCase(Locale.ROOT))) {
            return false;
        }
        for (String option : mustContain.split("/")) {
            if (lower.contains(option.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

}
