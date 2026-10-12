package com.railmind.rag;

import java.util.List;

/**
 * Provider-agnostic contract for turning text into a fixed-length vector,
 * mirroring the ai.provider.AIProvider pattern: nothing outside this package
 * knows which embedding backend is active.
 */
public interface EmbeddingProvider {

    /** Stable id used in railmind.rag.embedding-provider (e.g. "openai", "hashing"). */
    String getName();

    /** False if required config (e.g. an API key) is missing - the resolver falls back to "hashing" in that case. */
    boolean isConfigured();

    /** Length of every vector this provider returns. Must match the `vector(N)` column width in V8__policy_rag.sql. */
    int dimensions();

    float[] embed(String text);

    List<float[]> embedBatch(List<String> texts);
}
