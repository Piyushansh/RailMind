package com.railmind.rag;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PolicyRepositories {

    interface PolicyDocumentRepository extends JpaRepository<PolicyDocument, Long> {
        List<PolicyDocument> findByCategory(PolicyCategory category);
        long countByCategory(PolicyCategory category);
    }

    interface PolicyChunkRepository extends JpaRepository<PolicyChunk, Long> {
        List<PolicyChunk> findByDocumentId(Long documentId);
        Optional<PolicyChunk> findByChunkId(String chunkId);
        List<PolicyChunk> findByIdIn(List<Long> ids);
    }
}
