package com.railmind.rag;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Metadata for one chunk of a policy document. The embedding itself is NOT
 * mapped here - Hibernate's type system doesn't cleanly own pgvector's
 * `vector` column across versions, so PolicyChunkVectorStore writes/reads it
 * directly via JDBC after this row is persisted. Every column the spec asks
 * for (document_name via the FK, type/category, section, page, chunk_id,
 * created_at) lives on this entity or its parent PolicyDocument.
 */
@Getter
@Setter
@Entity
@Table(name = "policy_chunks")
public class PolicyChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private PolicyDocument document;

    @Column(name = "chunk_id", nullable = false, unique = true)
    private String chunkId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    private String section;

    private Integer page;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
