-- Supports real RAG usage analytics (previously untracked - each policy
-- question is now logged so /api/v1/admin/analytics/rag reports actual
-- query volume and grounding rate rather than only static corpus stats).

CREATE TABLE policy_queries (
    id BIGSERIAL PRIMARY KEY,
    question VARCHAR(500) NOT NULL,
    grounded BOOLEAN NOT NULL,
    top_similarity NUMERIC(6,4),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_policy_queries_created ON policy_queries(created_at);
