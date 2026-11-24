package com.hezron.smecommerce;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Stands in for Groq in every test. It repeats everything it was given, so if another shop's text ever
 * reached the prompt, it would show up in the answer and the leak test would fail.
 * A prompt containing {@link #FAIL_MARKER} throws, like the provider being down.
 */
public class FakeChatModel implements ChatModel {

    public static final String FAIL_MARKER = "CHAT-FAILS-HERE";
    public static final int INPUT_TOKENS = 10;
    public static final int OUTPUT_TOKENS = 5;

    private final AtomicInteger calls = new AtomicInteger();

    public int calls() {
        return calls.get();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        calls.incrementAndGet();
        StringBuilder everything = new StringBuilder();
        List<Message> messages = prompt.getInstructions();
        for (Message message : messages) {
            everything.append(message.getText()).append('\n');
        }
        String text = everything.toString();
        if (text.contains(FAIL_MARKER)) {
            throw new IllegalStateException("Fake chat provider is down");
        }
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .usage(new DefaultUsage(INPUT_TOKENS, OUTPUT_TOKENS))
                .build();
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))), metadata);
    }

}
