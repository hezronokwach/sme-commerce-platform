package com.hezron.smecommerce.assistant.internal.application;

import com.hezron.smecommerce.catalog.CatalogApi;
import com.hezron.smecommerce.catalog.StorefrontShop;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * Answers a shopper's question from the shop's own documents (Guide 7.2).
 * <p>
 * The order matters: the cheap checks come first, and the model is only called when this shop's
 * documents have something relevant. No database transaction is open during the slow calls.
 * Questions are never logged: customers may type personal details into them.
 */
@Service
@EnableConfigurationProperties(AssistantProperties.class)
public class AssistantAnswerService {

    static final String DONT_KNOW = "I don't have that information. Please contact the shop directly.";
    static final String RESTING = "The assistant is resting for today. Please contact the shop directly.";
    static final String UNAVAILABLE = "The assistant can't answer right now. Please try again in a moment.";

    private static final Logger log = LoggerFactory.getLogger(AssistantAnswerService.class);

    private final CatalogApi catalogApi;
    private final DocumentChunks chunks;
    private final AssistantUsage usage;
    private final ChatModel chatModel;
    private final AssistantProperties settings;
    private final QuestionRateLimiter rateLimiter;

    AssistantAnswerService(CatalogApi catalogApi, DocumentChunks chunks, AssistantUsage usage, ChatModel chatModel,
            AssistantProperties settings) {
        this.catalogApi = catalogApi;
        this.chunks = chunks;
        this.usage = usage;
        this.chatModel = chatModel;
        this.settings = settings;
        this.rateLimiter = new QuestionRateLimiter(settings.questionsPerMinute(), Clock.systemUTC());
    }

    /**
     * @param shopSlug the storefront's web name; the shop comes from here, never from the request body
     * @param caller   who is asking (their IP address), for the per-minute limit
     * @throws TooManyQuestionsException if this caller asked too often this minute
     */
    public AnswerResult ask(String shopSlug, String question, String caller) {
        StorefrontShop shop = catalogApi.findStorefrontShop(shopSlug);
        UUID tenantId = shop.tenantId().value();

        if (!rateLimiter.tryAcquire(tenantId + ":" + caller)) {
            throw new TooManyQuestionsException();
        }
        if (!usage.tryCountQuestion(tenantId, settings.dailyQuestions(), settings.dailyTokens())) {
            return new AnswerResult(RESTING, List.of());
        }

        try {
            List<Document> matches = chunks.search(tenantId, question, settings.maxMatches(),
                    settings.minSimilarity());
            if (matches.isEmpty()) {
                return new AnswerResult(DONT_KNOW, List.of()); // no model call, no cost
            }
            return askModel(tenantId, shop.name(), question, matches);
        } catch (RuntimeException e) {
            // Only the error's type: its message could quote the request.
            log.warn("The assistant couldn't answer for shop {}: {}", tenantId, e.getClass().getSimpleName());
            return new AnswerResult(UNAVAILABLE, List.of());
        }
    }

    private AnswerResult askModel(UUID tenantId, String shopName, String question, List<Document> matches) {
        Prompt prompt = new Prompt(List.of(new SystemMessage(AssistantPrompt.instructions(shopName, matches)),
                new UserMessage(question)));
        ChatResponse response = chatModel.call(prompt);

        Usage tokens = response.getMetadata().getUsage();
        usage.addTokens(tenantId, zeroIfNull(tokens.getPromptTokens()), zeroIfNull(tokens.getCompletionTokens()));

        String answer = response.getResult().getOutput().getText();
        if (answer == null || answer.isBlank()) {
            return new AnswerResult(DONT_KNOW, List.of());
        }
        return new AnswerResult(answer.strip(), distinctTitles(matches));
    }

    private static List<String> distinctTitles(List<Document> matches) {
        List<String> titles = new ArrayList<>();
        for (Document match : matches) {
            String title = AssistantPrompt.titleOf(match);
            if (!titles.contains(title)) {
                titles.add(title);
            }
        }
        return titles;
    }

    private static long zeroIfNull(Integer tokens) {
        if (tokens == null) {
            return 0;
        }
        return tokens;
    }

}
