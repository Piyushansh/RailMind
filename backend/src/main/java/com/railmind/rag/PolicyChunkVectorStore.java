package com.railmind.rag;

import com.pgvector.PGvector;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * Writes and queries the `embedding vector(1536)` column on policy_chunks
 * directly via JDBC, since that column deliberately isn't JPA-mapped (see
 * PolicyChunk). Cosine distance (`<=>`) is used for similarity ranking -
 * pgvector also supports L2 (`<->`) and inner product (`<#>`); cosine is the
 * right choice here since our embeddings are L2-normalized (both the OpenAI
 * and hashing-trick providers) and we care about direction, not magnitude.
 */
@Repository
public class PolicyChunkVectorStore {

    private final JdbcTemplate jdbcTemplate;

    public PolicyChunkVectorStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void storeEmbedding(Long chunkId, float[] embedding) {
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement("UPDATE policy_chunks SET embedding = ? WHERE id = ?");
            ps.setObject(1, new PGvector(embedding));
            ps.setLong(2, chunkId);
            return ps;
        });
    }

    /** @return chunk ids ordered by ascending cosine distance (closest/most similar first), each with its similarity score in [0,1] (1 = identical direction). */
    public List<Match> topMatches(float[] queryEmbedding, int limit) {
        String sql = """
                SELECT id, 1 - (embedding <=> ?) AS similarity
                FROM policy_chunks
                WHERE embedding IS NOT NULL
                ORDER BY embedding <=> ?
                LIMIT ?
                """;
        PGvector vector = new PGvector(queryEmbedding);
        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new Match(rs.getLong("id"), rs.getDouble("similarity")),
                vector, vector, limit);
    }

    public record Match(Long chunkId, double similarity) {}
}
