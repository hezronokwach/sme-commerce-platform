package com.hezron.smecommerce.assistant.internal.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The assistant's search settings and cost limits (Guide 7.2).
 *
 * @param maxMatches         how many chunks are given to the model
 * @param minSimilarity      chunks less similar than this (0 to 1) are ignored
 * @param questionsPerMinute per shop and caller
 * @param dailyQuestions     per shop, per UTC day
 * @param dailyTokens        input + output tokens per shop, per UTC day
 */
@ConfigurationProperties("app.assistant")
public record AssistantProperties(int maxMatches, double minSimilarity, int questionsPerMinute, int dailyQuestions,
        long dailyTokens) {
}
