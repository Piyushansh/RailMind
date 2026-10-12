-- Real RAG storage. Embeddings live in a native `vector` column (pgvector
-- extension), queried via plain JDBC (see rag.PolicyChunkVectorStore) rather
-- than through Hibernate, since mapping pgvector's type cleanly through JPA
-- is fragile across Hibernate versions - the metadata columns stay ordinary
-- JPA-managed columns; only the embedding itself bypasses the ORM.
--
-- Dimension is fixed at 1536 so the schema doesn't change based on which
-- embedding provider is active (OpenAI text-embedding-3-small, or the
-- deterministic offline fallback in HashingEmbeddingProvider, both produce
-- 1536-dim vectors - see railmind.rag.embedding-dimensions).

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE policy_documents (
    id BIGSERIAL PRIMARY KEY,
    document_name VARCHAR(255) NOT NULL,
    category VARCHAR(40) NOT NULL,   -- CANCELLATION, REFUNDS, BOOKING, WAITING_LIST, QUOTA, PASSENGER, BAGGAGE, GENERAL_TRAVEL
    source_type VARCHAR(20) NOT NULL DEFAULT 'SEED',  -- SEED, ADMIN_UPLOAD
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_policy_documents_category ON policy_documents(category);

CREATE TABLE policy_chunks (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES policy_documents(id) ON DELETE CASCADE,
    chunk_id VARCHAR(64) NOT NULL UNIQUE,
    chunk_index INT NOT NULL,
    section VARCHAR(255),
    page INT,
    content TEXT NOT NULL,
    embedding vector(1536),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_policy_chunks_document ON policy_chunks(document_id);
-- IVFFlat needs rows to train lists against; for a demo-scale corpus (dozens to a
-- few hundred chunks) a sequential scan via the <-> operator is fine, so no ANN
-- index is created here. Add one (e.g. `USING ivfflat (embedding vector_cosine_ops)`)
-- once the corpus is large enough for it to matter.
