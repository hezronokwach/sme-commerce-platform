package com.hezron.smecommerce.assistant.internal.application;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Each shop's daily AI use (assistant.usage_daily), which keeps costs under a limit. */
@Component
class AssistantUsage {

    private final JdbcTemplate jdbc;

    AssistantUsage(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Counts one question, unless the shop is at a daily limit. It is one SQL statement, so two questions
     * at the same moment can't both slip past the limit.
     *
     * @return false if the shop has used up today's budget
     */
    boolean tryCountQuestion(UUID tenantId, int maxQuestions, long maxTokens) {
        int rows = jdbc.update("""
                INSERT INTO assistant.usage_daily AS usage (tenant_id, day, questions)
                VALUES (?, ?, 1)
                ON CONFLICT (tenant_id, day) DO UPDATE SET questions = usage.questions + 1
                WHERE usage.questions < ? AND usage.input_tokens + usage.output_tokens < ?
                """, tenantId, today(), maxQuestions, maxTokens);
        return rows == 1;
    }

    void addTokens(UUID tenantId, long inputTokens, long outputTokens) {
        jdbc.update("""
                UPDATE assistant.usage_daily
                SET input_tokens = input_tokens + ?, output_tokens = output_tokens + ?
                WHERE tenant_id = ? AND day = ?
                """, inputTokens, outputTokens, tenantId, today());
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

}
