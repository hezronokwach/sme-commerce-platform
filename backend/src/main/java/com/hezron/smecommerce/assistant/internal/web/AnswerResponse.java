package com.hezron.smecommerce.assistant.internal.web;

import com.hezron.smecommerce.assistant.internal.application.AnswerResult;
import java.util.List;

/** @param sources titles of the shop's documents behind the answer */
record AnswerResponse(String answer, List<String> sources) {

    static AnswerResponse fromResult(AnswerResult result) {
        return new AnswerResponse(result.answer(), result.sources());
    }

}
