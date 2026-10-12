package com.railmind.rag;

import com.railmind.rag.PolicyChunkVectorStore.Match;
import com.railmind.rag.PolicyRepositories.PolicyChunkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Locks in the spec's hallucination guard: PolicyRetrievalService must drop
 * anything below minSimilarity, and return an empty list (not a low-quality
 * best-effort match) when nothing clears the bar - PolicyRagService/the
 * policySearch tool both depend on "empty" meaning "say I don't know",
 * not "here's my best guess".
 */
class PolicyRetrievalServiceTest {

    private static final double MIN_SIMILARITY = 0.2;

    private PolicyChunkVectorStore vectorStore;
    private PolicyChunkRepository chunkRepository;
    private HashingEmbeddingProvider hashingProvider;
    private PolicyRetrievalService service;

    @BeforeEach
    void setUp() {
        vectorStore = mock(PolicyChunkVectorStore.class);
        chunkRepository = mock(PolicyChunkRepository.class);
        hashingProvider = new HashingEmbeddingProvider();

        EmbeddingProviderResolver resolver = mock(EmbeddingProviderResolver.class);
        when(resolver.resolve()).thenReturn(hashingProvider);

        service = new PolicyRetrievalService(vectorStore, chunkRepository, resolver, 5, MIN_SIMILARITY);
    }

    @Test
    void allMatchesBelowThreshold_returnsEmptyNotBestEffort() {
        when(vectorStore.topMatches(any(float[].class), anyInt()))
                .thenReturn(List.of(new Match(1L, 0.05), new Match(2L, 0.1)));

        List<PolicyRetrievalService.RetrievedChunk> result = service.retrieve("some unrelated query");

        assertThat(result).isEmpty();
        // an empty result must short-circuit before hitting the DB for chunk content
        org.mockito.Mockito.verifyNoInteractions(chunkRepository);
    }

    @Test
    void matchesAboveThreshold_areReturnedWithSourceMetadata() {
        PolicyDocument doc = new PolicyDocument();
        doc.setId(10L);
        doc.setDocumentName("Refund Policy");
        doc.setCategory(PolicyCategory.REFUNDS);

        PolicyChunk chunk = new PolicyChunk();
        chunk.setId(1L);
        chunk.setChunkId("chunk_abc123");
        chunk.setDocument(doc);
        chunk.setContent("Refunds are calculated based on hours before departure.");

        when(vectorStore.topMatches(any(float[].class), anyInt()))
                .thenReturn(List.of(new Match(1L, 0.42), new Match(2L, 0.05))); // one above, one below threshold
        when(chunkRepository.findByIdIn(List.of(1L))).thenReturn(List.of(chunk));

        List<PolicyRetrievalService.RetrievedChunk> result = service.retrieve("how are refunds calculated");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).chunkId()).isEqualTo("chunk_abc123");
        assertThat(result.get(0).documentName()).isEqualTo("Refund Policy");
        assertThat(result.get(0).category()).isEqualTo(PolicyCategory.REFUNDS);
        assertThat(result.get(0).similarity()).isEqualTo(0.42);
    }

    @Test
    void noMatchesAtAll_returnsEmpty() {
        when(vectorStore.topMatches(any(float[].class), anyInt())).thenReturn(List.of());

        assertThat(service.retrieve("anything")).isEmpty();
    }
}
