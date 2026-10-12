package com.railmind.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Splits a document's plain text into overlapping chunks along sentence
 * boundaries where possible, targeting ~chunkSizeChars characters with
 * ~overlapChars of overlap so a fact split across two sentences isn't lost
 * entirely to one chunk.
 */
@Component
public class DocumentChunker {

    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("(?<=[.!?])\\s+");

    public record Chunk(int index, String content) {}

    public List<Chunk> chunk(String text, int chunkSizeChars, int overlapChars) {
        String[] sentences = SENTENCE_BOUNDARY.split(text.strip());
        List<Chunk> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int index = 0;

        for (String sentence : sentences) {
            if (current.length() + sentence.length() + 1 > chunkSizeChars && !current.isEmpty()) {
                chunks.add(new Chunk(index++, current.toString().strip()));
                // carry the tail of the previous chunk forward as overlap
                String tail = current.length() > overlapChars
                        ? current.substring(current.length() - overlapChars)
                        : current.toString();
                current = new StringBuilder(tail);
            }
            current.append(sentence).append(' ');
        }
        if (!current.toString().isBlank()) {
            chunks.add(new Chunk(index, current.toString().strip()));
        }
        return chunks;
    }
}
