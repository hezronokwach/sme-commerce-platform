package com.hezron.smecommerce.assistant.internal.application;

import java.util.List;

/**
 * The assistant's reply.
 *
 * @param sources the titles of the shop's documents it was given; empty if the model wasn't asked
 */
public record AnswerResult(String answer, List<String> sources) {
}
