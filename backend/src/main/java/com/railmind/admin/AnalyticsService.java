package com.railmind.admin;

import com.railmind.admin.dto.AnalyticsDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.util.List;

/**
 * Every number here comes from an actual SQL aggregation against the real
 * schema - nothing in this class returns a hardcoded or invented value.
 * Uses plain JdbcTemplate rather than JPQL/repositories: this is read-only
 * reporting across several tables with GROUP BY/date-bucketing that doesn't
 * touch domain invariants, so native SQL is the right tool rather than
 * bending the entity model to fit reporting needs (the pattern already used
 * for pgvector similarity search in this codebase).
 */
@Service
public class AnalyticsService {

    private final JdbcTemplate jdbc;

    public AnalyticsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public OverviewDto overview() {
        long total = count("SELECT COUNT(*) FROM bookings");
        long confirmed = count("SELECT COUNT(*) FROM bookings WHERE status = 'CONFIRMED'");
        long cancelled = count("SELECT COUNT(*) FROM bookings WHERE status = 'CANCELLED'");
        long pending = count("SELECT COUNT(*) FROM bookings WHERE status = 'PENDING_PAYMENT'");
        BigDecimal revenue = amount("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE status = 'SUCCESS'");
        BigDecimal refunded = amount("SELECT COALESCE(SUM(refund_amount), 0) FROM refunds");
        long activeUsers = count("SELECT COUNT(DISTINCT user_id) FROM bookings WHERE created_at > now() - interval '30 days'");
        double cancellationRate = total == 0 ? 0.0 : Math.round((cancelled * 10000.0 / total)) / 100.0;

        return new OverviewDto(total, confirmed, cancelled, pending, revenue, refunded, activeUsers, cancellationRate);
    }

    public BookingsAnalyticsDto bookings() {
        List<StatusCount> byStatus = jdbc.query(
                "SELECT status, COUNT(*) AS c FROM bookings GROUP BY status ORDER BY c DESC",
                (rs, i) -> new StatusCount(rs.getString("status"), rs.getLong("c")));

        List<DailyCount> trend = jdbc.query("""
                SELECT date_trunc('day', created_at)::date AS day, COUNT(*) AS c
                FROM bookings
                WHERE created_at > now() - interval '30 days'
                GROUP BY day ORDER BY day
                """, (rs, i) -> new DailyCount(rs.getDate("day").toLocalDate(), rs.getLong("c")));

        return new BookingsAnalyticsDto(byStatus, trend);
    }

    public RevenueAnalyticsDto revenue() {
        List<DailyAmount> daily = jdbc.query("""
                SELECT date_trunc('day', created_at)::date AS day, COALESCE(SUM(amount), 0) AS total
                FROM payments
                WHERE status = 'SUCCESS' AND created_at > now() - interval '30 days'
                GROUP BY day ORDER BY day
                """, (rs, i) -> new DailyAmount(rs.getDate("day").toLocalDate(), rs.getBigDecimal("total")));

        BigDecimal totalRefunded = amount("SELECT COALESCE(SUM(refund_amount), 0) FROM refunds");
        return new RevenueAnalyticsDto(daily, totalRefunded);
    }

    public RoutesAnalyticsDto routes() {
        List<RoutePopularity> top = jdbc.query("""
                SELECT src.code AS source_code, dst.code AS dest_code, COUNT(*) AS c
                FROM bookings b
                JOIN stations src ON src.id = b.source_station_id
                JOIN stations dst ON dst.id = b.destination_station_id
                GROUP BY src.code, dst.code
                ORDER BY c DESC
                LIMIT 10
                """, (rs, i) -> new RoutePopularity(rs.getString("source_code"), rs.getString("dest_code"), rs.getLong("c")));
        return new RoutesAnalyticsDto(top);
    }

    public CancellationsAnalyticsDto cancellations() {
        long totalCancellations = count("SELECT COUNT(*) FROM cancellations");
        BigDecimal totalRefundAmount = amount("SELECT COALESCE(SUM(refund_amount), 0) FROM refunds");
        Double avgDeduction = jdbc.queryForObject(
                "SELECT COALESCE(AVG(deduction_percent), 0) FROM refunds", Double.class);
        double avg = avgDeduction == null ? 0.0 : Math.round(avgDeduction * 100.0) / 100.0;
        return new CancellationsAnalyticsDto(totalCancellations, totalRefundAmount, avg);
    }

    public AiAnalyticsDto ai() {
        long conversations = count("SELECT COUNT(*) FROM ai_conversations");
        long messages = count("SELECT COUNT(*) FROM ai_messages");
        long toolExecutions = count("SELECT COUNT(*) FROM ai_tool_executions");
        long successful = count("SELECT COUNT(*) FROM ai_tool_executions WHERE success = true");
        long failed = count("SELECT COUNT(*) FROM ai_tool_executions WHERE success = false");
        return new AiAnalyticsDto(conversations, messages, toolExecutions, successful, failed);
    }

    public RagAnalyticsDto rag() {
        long total = count("SELECT COUNT(*) FROM policy_queries");
        long grounded = count("SELECT COUNT(*) FROM policy_queries WHERE grounded = true");
        long insufficient = count("SELECT COUNT(*) FROM policy_queries WHERE grounded = false");
        Double avgSimilarity = jdbc.queryForObject(
                "SELECT AVG(top_similarity) FROM policy_queries WHERE grounded = true", Double.class);
        return new RagAnalyticsDto(total, grounded, insufficient, avgSimilarity);
    }

    public ToolsAnalyticsDto tools() {
        List<ToolBreakdown> breakdown = jdbc.query("""
                SELECT tool_name,
                       COUNT(*) AS total,
                       SUM(CASE WHEN success THEN 1 ELSE 0 END) AS successes,
                       SUM(CASE WHEN success THEN 0 ELSE 1 END) AS failures
                FROM ai_tool_executions
                GROUP BY tool_name
                ORDER BY total DESC
                """, (rs, i) -> new ToolBreakdown(rs.getString("tool_name"), rs.getLong("total"),
                        rs.getLong("successes"), rs.getLong("failures")));
        return new ToolsAnalyticsDto(breakdown);
    }

    private long count(String sql) {
        Long result = jdbc.queryForObject(sql, Long.class);
        return result == null ? 0 : result;
    }

    private BigDecimal amount(String sql) {
        BigDecimal result = jdbc.queryForObject(sql, BigDecimal.class);
        return result == null ? BigDecimal.ZERO : result.setScale(2, RoundingMode.HALF_UP);
    }
}
