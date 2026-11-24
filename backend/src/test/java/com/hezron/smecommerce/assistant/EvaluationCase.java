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

    /** The same text as AssistantAnswerService.UNAVAILABLE, which is internal to the module. */
    static final String UNAVAILABLE = "The assistant can't answer right now. Please try again in a moment.";

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
        // "Can't answer right now" means the model wasn't reached: never a pass, even when it happens
        // to contain an expected word such as "can't".
        if (answer.equals(UNAVAILABLE)) {
            return false;
        }
        String text = simplify(answer);
        if (!mustNotContain.isEmpty() && text.contains(simplify(mustNotContain))) {
            return false;
        }
        for (String option : mustContain.split("/")) {
            if (text.contains(simplify(option))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Lower case, with every kind of space left out and typographic apostrophes and dashes made plain.
     * So "9 am" matches "9am", and "don’t" matches "don't": we score what the answer says, not its typing.
     */
    static String simplify(String text) {
        StringBuilder simple = new StringBuilder();
        for (char c : text.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isWhitespace(c) || Character.isSpaceChar(c) || Character.getType(c) == Character.FORMAT) {
                continue; // includes no-break and zero-width spaces
            }
            if (c == '‘' || c == '’') {
                simple.append('\'');
            } else if (c >= '‐' && c <= '―') {
                simple.append('-');
            } else {
                simple.append(c);
            }
        }
        return simple.toString();
    }

}
