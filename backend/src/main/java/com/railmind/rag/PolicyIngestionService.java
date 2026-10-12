package com.railmind.rag;

import com.railmind.audit.AuditService;
import com.railmind.rag.PolicyRepositories.PolicyChunkRepository;
import com.railmind.rag.PolicyRepositories.PolicyDocumentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

/**
 * The real ingestion pipeline: Document -> chunk -> embed -> store, per the
 * spec's required architecture. Used both by admin document uploads and by
 * PolicySeedDataRunner at startup.
 */
@Service
public class PolicyIngestionService {

    private final PolicyDocumentRepository documentRepository;
    private final PolicyChunkRepository chunkRepository;
    private final PolicyChunkVectorStore vectorStore;
    private final DocumentChunker chunker;
    private final EmbeddingProviderResolver embeddingProviderResolver;
    private final AuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();

    private final int chunkSizeChars;
    private final int overlapChars;

    public PolicyIngestionService(PolicyDocumentRepository documentRepository, PolicyChunkRepository chunkRepository,
                                   PolicyChunkVectorStore vectorStore, DocumentChunker chunker,
                                   EmbeddingProviderResolver embeddingProviderResolver, AuditService auditService,
                                   @Value("${railmind.rag.chunk-size-chars:800}") int chunkSizeChars,
                                   @Value("${railmind.rag.chunk-overlap-chars:150}") int overlapChars) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.vectorStore = vectorStore;
        this.chunker = chunker;
        this.embeddingProviderResolver = embeddingProviderResolver;
        this.auditService = auditService;
        this.chunkSizeChars = chunkSizeChars;
        this.overlapChars = overlapChars;
    }

    @Transactional
    public PolicyDocument ingest(String documentName, PolicyCategory category, String sourceType, String rawText,
                                  Long actorUserId) {
        PolicyDocument document = new PolicyDocument();
        document.setDocumentName(documentName);
        document.setCategory(category);
        document.setSourceType(sourceType);
        document = documentRepository.save(document);

        List<DocumentChunker.Chunk> textChunks = chunker.chunk(rawText, chunkSizeChars, overlapChars);
        EmbeddingProvider embeddingProvider = embeddingProviderResolver.resolve();
        List<float[]> embeddings = embeddingProvider.embedBatch(textChunks.stream().map(DocumentChunker.Chunk::content).toList());

        for (int i = 0; i < textChunks.size(); i++) {
            DocumentChunker.Chunk textChunk = textChunks.get(i);
            PolicyChunk chunk = new PolicyChunk();
            chunk.setDocument(document);
            chunk.setChunkId(generateChunkId());
            chunk.setChunkIndex(textChunk.index());
            chunk.setContent(textChunk.content());
            chunk = chunkRepository.save(chunk);
            vectorStore.storeEmbedding(chunk.getId(), embeddings.get(i));
        }

        auditService.log(actorUserId, "POLICY_DOCUMENT_INGESTED", "PolicyDocument", document.getId(),
                "category=" + category + " chunks=" + textChunks.size() + " embeddingProvider=" + embeddingProvider.getName());
        return document;
    }

    private String generateChunkId() {
        byte[] bytes = new byte[9];
        secureRandom.nextBytes(bytes);
        return "chunk_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
