package com.railmind.rag;

import com.railmind.rag.PolicyRepositories.PolicyChunkRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Similarity retrieval + the hallucination guard the spec requires: if
 * nothing clears minSimilarity, callers get an empty result and MUST fall
 * back to a safe "insufficient information" response rather than asking the
 * LLM to answer from unguided knowledge.
 */
@Service
public class PolicyRetrievalService {

    private final PolicyChunkVectorStore vectorStore;
    private final PolicyChunkRepository chunkRepository;
    private final EmbeddingProviderResolver embeddingProviderResolver;
    private final int topK;
    private final double minSimilarity;

    public PolicyRetrievalService(PolicyChunkVectorStore vectorStore, PolicyChunkRepository chunkRepository,
                                   EmbeddingProviderResolver embeddingProviderResolver,
                                   @Value("${railmind.rag.top-k:5}") int topK,
                                   @Value("${railmind.rag.min-similarity:0.15}") double minSimilarity) {
        this.vectorStore = vectorStore;
        this.chunkRepository = chunkRepository;
        this.embeddingProviderResolver = embeddingProviderResolver;
        this.topK = topK;
        this.minSimilarity = minSimilarity;
    }

    public record RetrievedChunk(String chunkId, String documentName, PolicyCategory category, String section,
                                  Integer page, String content, double similarity) {}

    @Transactional(readOnly = true)
    public List<RetrievedChunk> retrieve(String query) {
        float[] queryEmbedding = embeddingProviderResolver.resolve().embed(query);
        List<PolicyChunkVectorStore.Match> matches = vectorStore.topMatches(queryEmbedding, topK).stream()
                .filter(m -> m.similarity() >= minSimilarity)
                .toList();
        if (matches.isEmpty()) {
            return List.of();
        }

        Map<Long, PolicyChunk> chunksById = chunkRepository.findByIdIn(matches.stream().map(PolicyChunkVectorStore.Match::chunkId).toList())
                .stream().collect(Collectors.toMap(PolicyChunk::getId, c -> c));

        return matches.stream()
                .map(m -> {
                    PolicyChunk chunk = chunksById.get(m.chunkId());
                    if (chunk == null) return null; // stale index entry, ignore
                    return new RetrievedChunk(chunk.getChunkId(), chunk.getDocument().getDocumentName(),
                            chunk.getDocument().getCategory(), chunk.getSection(), chunk.getPage(),
                            chunk.getContent(), m.similarity());
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }
}
