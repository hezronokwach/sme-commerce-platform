package com.hezron.smecommerce.assistant.internal.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** How texts are cut into pieces for the vector store. No Spring, no database. */
class TextChunkerTests {

    @Test
    void aShortTextIsOnePiece() {
        List<String> chunks = TextChunker.chunk("We deliver in Nairobi on weekdays.");

        assertThat(chunks).containsExactly("We deliver in Nairobi on weekdays.");
    }

    @Test
    void anEmptyOrBlankTextHasNoPieces() {
        assertThat(TextChunker.chunk("")).isEmpty();
        assertThat(TextChunker.chunk("   \n\t ")).isEmpty();
        assertThat(TextChunker.chunk(null)).isEmpty();
    }

    @Test
    void extraWhitespaceAndLineBreaksBecomeSingleSpaces() {
        List<String> chunks = TextChunker.chunk("  Returns:\n\n  within   7 days.\t");

        assertThat(chunks).containsExactly("Returns: within 7 days.");
    }

    @Test
    void aLongTextIsCutIntoOverlappingPiecesOfTheRightSize() {
        String text = words(1000); // w1 ... w1000

        List<String> chunks = TextChunker.chunk(text);

        // 400 words each, starting 350 apart: 1-400, 351-750, 701-1000.
        assertThat(chunks).hasSize(3);
        assertThat(wordCount(chunks.get(0))).isEqualTo(400);
        assertThat(wordCount(chunks.get(1))).isEqualTo(400);
        assertThat(wordCount(chunks.get(2))).isEqualTo(300);
        assertThat(chunks.get(0)).startsWith("w1 ").endsWith(" w400");
        assertThat(chunks.get(1)).startsWith("w351 ").endsWith(" w750");
        assertThat(chunks.get(2)).startsWith("w701 ").endsWith(" w1000");
    }

    @Test
    void theLastPieceIsNotJustARepeatOfTheOverlap() {
        // Exactly one chunk's worth: one piece, not a second one holding only the last 50 words.
        assertThat(TextChunker.chunk(words(400))).hasSize(1);
        assertThat(TextChunker.chunk(words(401))).hasSize(2);
    }

    @Test
    void smallSizesWorkTheSameWay() {
        List<String> chunks = TextChunker.chunk("a b c d e f g", 3, 1);

        assertThat(chunks).containsExactly("a b c", "c d e", "e f g");
    }

    private static String words(int count) {
        StringBuilder text = new StringBuilder();
        for (int i = 1; i <= count; i++) {
            if (i > 1) {
                text.append(' ');
            }
            text.append('w').append(i);
        }
        return text.toString();
    }

    private static int wordCount(String chunk) {
        return chunk.split(" ").length;
    }

}
