package com.railmind.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The properties that matter for a fallback embedding used in real vector
 * search: determinism (so re-ingesting the same text is idempotent),
 * L2-normalization (so PolicyChunkVectorStore's cosine-distance query is
 * meaningful), and some separation between unrelated text (so "cancellation"
 * and "baggage" don't collapse to the same vector).
 */
class HashingEmbeddingProviderTest {

    private final HashingEmbeddingProvider provider = new HashingEmbeddingProvider();

    @Test
    void sameText_alwaysProducesTheSameVector() {
        float[] a = provider.embed("Refunds are calculated based on hours before departure.");
        float[] b = provider.embed("Refunds are calculated based on hours before departure.");
        assertThat(a).containsExactly(b);
    }

    @Test
    void vectorLength_matchesDeclaredDimensions() {
        float[] vector = provider.embed("any text");
        assertThat(vector).hasSize(provider.dimensions());
    }

    @Test
    void nonEmptyText_producesAUnitNormVector() {
        float[] vector = provider.embed("cancellation refund policy waiting list quota");
        double sumSquares = 0;
        for (float v : vector) sumSquares += (double) v * v;
        assertThat(Math.sqrt(sumSquares)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-4));
    }

    @Test
    void differentText_producesDifferentVectors() {
        float[] cancellation = provider.embed("How do I cancel my train booking and get a refund");
        float[] baggage = provider.embed("What is the baggage allowance for my journey");
        assertThat(cancellation).isNotEqualTo(baggage);
    }

    @Test
    void isAlwaysConfigured_soItCanServeAsTheDefaultFallback() {
        assertThat(provider.isConfigured()).isTrue();
    }
}
