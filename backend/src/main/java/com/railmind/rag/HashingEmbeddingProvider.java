package com.railmind.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Deterministic offline fallback embedding using the "hashing trick" (feature
 * hashing): each token is hashed into one of N buckets, sign-hashed to reduce
 * collision bias, and the resulting bag-of-words vector is L2-normalized.
 * This is a real, well-established technique (used in scikit-learn's
 * HashingVectorizer and Vowpal Wabbit) - it is NOT a neural embedding and
 * will not capture semantic similarity the way OpenAI's model does, only
 * lexical/keyword overlap. It exists so RAG remains fully functional (real
 * chunking, real vector storage, real cosine search) when no embedding API
 * key is configured, the same role MockAIProvider plays for chat.
 *
 * Always available (isConfigured() is always true), and used as the default
 * railmind.rag.embedding-provider so the app runs out of the box.
 */
@Component
public class HashingEmbeddingProvider implements EmbeddingProvider {

    private static final int DIMENSIONS = 1536;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-z0-9]+");

    @Override
    public String getName() {
        return "hashing";
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[DIMENSIONS];
        var matcher = TOKEN_PATTERN.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String token = matcher.group();
            int hash = token.hashCode();
            int bucket = Math.floorMod(hash, DIMENSIONS);
            // A second, independent hash decides the sign, which keeps the expected
            // inner product of two unrelated tokens close to zero instead of always positive.
            float sign = (Math.floorMod(hash * 2654435761L, 2) == 0) ? 1f : -1f;
            vector[bucket] += sign;
        }
        normalize(vector);
        return vector;
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (String text : texts) {
            vectors.add(embed(text));
        }
        return vectors;
    }

    private void normalize(float[] vector) {
        double sumSquares = 0;
        for (float v : vector) sumSquares += (double) v * v;
        if (sumSquares == 0) return;
        float norm = (float) Math.sqrt(sumSquares);
        for (int i = 0; i < vector.length; i++) vector[i] /= norm;
    }
}
