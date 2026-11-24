package com.hezron.smecommerce.assistant.internal.application;

import java.util.ArrayList;
import java.util.List;

/**
 * Cuts a text into overlapping pieces for the vector store (Guide 7.1: 300 to 500 words, overlapping by
 * about 50).
 * <p>
 * Why pieces at all: a search finds the few pieces closest to a question, and only those go to the chat
 * model. One huge piece per document would match everything a little and nothing well.
 * Why overlap: a sentence cut in half at a boundary still appears whole in one of the two pieces.
 * <p>
 * Words are split on any whitespace and joined with single spaces, so line breaks aren't kept. That's
 * fine for search: the model reads the words, not the layout.
 */
public final class TextChunker {

    public static final int WORDS_PER_CHUNK = 400;
    public static final int OVERLAP_WORDS = 50;

    private TextChunker() {
    }

    /** The pieces, in order. An empty or blank text gives none; a short text gives one. */
    public static List<String> chunk(String text) {
        return chunk(text, WORDS_PER_CHUNK, OVERLAP_WORDS);
    }

    static List<String> chunk(String text, int wordsPerChunk, int overlapWords) {
        if (overlapWords >= wordsPerChunk) {
            throw new IllegalArgumentException("The overlap must be smaller than the chunk");
        }
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        String[] words = text.strip().split("\\s+");

        // Each piece starts this many words after the previous one.
        int step = wordsPerChunk - overlapWords;
        int start = 0;
        while (start < words.length) {
            int end = start + wordsPerChunk;
            if (end > words.length) {
                end = words.length;
            }
            chunks.add(join(words, start, end));
            if (end == words.length) {
                break; // the last piece reached the end; another would only repeat its overlap
            }
            start = start + step;
        }
        return chunks;
    }

    private static String join(String[] words, int start, int end) {
        StringBuilder piece = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (i > start) {
                piece.append(' ');
            }
            piece.append(words[i]);
        }
        return piece.toString();
    }

}
