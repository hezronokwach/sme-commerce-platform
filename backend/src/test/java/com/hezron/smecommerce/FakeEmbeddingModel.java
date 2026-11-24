package com.hezron.smecommerce;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * Stands in for Gemini in every test: no network, no key, always the same numbers for the same text.
 * <p>
 * Each word adds 1 at a position picked from the word's hash, and the result is scaled to length 1. So
 * texts that share words get similar vectors, which is enough for the search tests in the next issue.
 * A text containing {@link #FAIL_MARKER} throws, like the provider being down.
 */
public class FakeEmbeddingModel implements EmbeddingModel {

    public static final int DIMENSIONS = 768;
    public static final String FAIL_MARKER = "EMBEDDING-FAILS-HERE";

    private final AtomicInteger textsEmbedded = new AtomicInteger();

    /** How many texts were turned into vectors so far (each chunk counts once). */
    public int textsEmbedded() {
        return textsEmbedded.get();
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        int index = 0;
        for (String text : request.getInstructions()) {
            embeddings.add(new Embedding(vectorFor(text), index));
            index = index + 1;
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return vectorFor(document.getText());
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    private float[] vectorFor(String text) {
        if (text != null && text.contains(FAIL_MARKER)) {
            throw new IllegalStateException("Fake embedding provider is down");
        }
        textsEmbedded.incrementAndGet();
        float[] vector = new float[DIMENSIONS];
        String words = "";
        if (text != null) {
            words = text.toLowerCase(Locale.ROOT);
        }
        for (String word : words.split("[^\\p{L}\\p{N}]+")) {
            if (word.isEmpty()) {
                continue;
            }
            int position = Math.floorMod(hash(word), DIMENSIONS);
            vector[position] = vector[position] + 1;
        }
        double length = 0;
        for (float value : vector) {
            length = length + value * value;
        }
        if (length == 0) {
            vector[0] = 1; // an empty text still gets a valid (non-zero) vector
            return vector;
        }
        double scale = 1 / Math.sqrt(length);
        for (int i = 0; i < DIMENSIONS; i++) {
            vector[i] = (float) (vector[i] * scale);
        }
        return vector;
    }

    /** A stable hash (String.hashCode is stable too, but this keeps the reason visible). */
    private static int hash(String word) {
        int h = 17;
        for (byte b : word.getBytes(StandardCharsets.UTF_8)) {
            h = h * 31 + b;
        }
        return h;
    }

}
