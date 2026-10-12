package com.railmind.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentChunkerTest {

    private final DocumentChunker chunker = new DocumentChunker();

    @Test
    void shortText_producesASingleChunk() {
        String text = "Refunds are issued to the original payment method. Processing can take a few days.";
        List<DocumentChunker.Chunk> chunks = chunker.chunk(text, 800, 150);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).content()).contains("Refunds are issued");
    }

    @Test
    void longText_splitsIntoMultipleChunksNoneWildlyOverTarget() {
        // 20 sentences of ~60 chars each = ~1200 chars, well over an 800-char target.
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            sb.append("This is policy sentence number ").append(i).append(" about railway rules. ");
        }

        List<DocumentChunker.Chunk> chunks = chunker.chunk(sb.toString(), 400, 80);

        assertThat(chunks.size()).isGreaterThan(1);
        // allow slack for the last sentence that pushed a chunk over the target, plus overlap carried in
        chunks.forEach(c -> assertThat(c.content().length()).isLessThan(400 + 200));
    }

    @Test
    void chunks_areIndexedSequentially() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            sb.append("Sentence ").append(i).append(" of the policy document text here. ");
        }
        List<DocumentChunker.Chunk> chunks = chunker.chunk(sb.toString(), 300, 50);

        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).index()).isEqualTo(i);
        }
    }

    @Test
    void blankText_producesNoChunks() {
        assertThat(chunker.chunk("   ", 800, 150)).isEmpty();
    }
}
