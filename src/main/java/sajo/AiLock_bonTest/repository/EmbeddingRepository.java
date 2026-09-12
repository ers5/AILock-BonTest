package sajo.AiLock_bonTest.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import sajo.AiLock_bonTest.dto.permit.SimilarRequest;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
@Slf4j
public class EmbeddingRepository {

    private final JdbcTemplate jdbcTemplate;

    public void saveTurnEmbedding(Long turnId, float[] embedding) {
        jdbcTemplate.update(
                "UPDATE chat_turns SET user_input_embedding = ?::vector WHERE id = ?",
                toVectorLiteral(embedding), turnId
        );
    }

    public void savePermitMemoryEmbedding(Long permitMemoryId, float[] embedding) {
        jdbcTemplate.update(
                "UPDATE permit_memory SET embedding = ?::vector WHERE id = ?",
                toVectorLiteral(embedding), permitMemoryId
        );
    }

    public List<String> findNearDupInputs(UUID deviceId, float[] embedding, Instant since, double threshold) {
        return jdbcTemplate.query(
                """
                SELECT user_input
                FROM chat_turns
                WHERE device_id = ?
                  AND created_at >= ?
                  AND user_input_embedding IS NOT NULL
                  AND is_embedding IS TRUE
                  AND (user_input_embedding <=> ?::vector) < ?
                ORDER BY created_at DESC
                """,
                (rs, n) -> rs.getString("user_input"),
                deviceId, Timestamp.from(since), toVectorLiteral(embedding), threshold
        );
    }

    private String toVectorLiteral(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 8);
        sb.append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    public List<SimilarRequest> searchSimilarWithOutcome(UUID deviceId, Long appId, float[] embedding, double threshold, int topN) {
        return jdbcTemplate.query(
                """
                SELECT retrieval_text, granted_sec, close_reason
                FROM (
                    SELECT retrieval_text, granted_sec, close_reason,
                           embedding <=> ?::vector AS distance
                    FROM permit_memory
                    WHERE device_id = ?
                      AND app_id = ?
                      AND embedding IS NOT NULL
                      AND created_at >= CURRENT_TIMESTAMP - INTERVAL '1 day'
                ) m
                WHERE distance < ?
                ORDER BY distance
                LIMIT ?
                """,
                (rs, rowNum) -> new SimilarRequest(
                        rs.getString("retrieval_text"),
                        rs.getInt("granted_sec"),
                        rs.getString("close_reason")
                ),
                toVectorLiteral(embedding), deviceId, appId, threshold, topN
        );
    }
}
