package com.railmind.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class AnalyticsDtos {

    public record OverviewDto(
            long totalBookings, long confirmedBookings, long cancelledBookings, long pendingPaymentBookings,
            BigDecimal totalRevenue, BigDecimal totalRefunded, long activeUsersLast30Days, double cancellationRate
    ) {}

    public record DailyCount(LocalDate day, long count) {}
    public record DailyAmount(LocalDate day, BigDecimal amount) {}

    public record BookingsAnalyticsDto(List<StatusCount> byStatus, List<DailyCount> dailyTrendLast30Days) {}
    public record StatusCount(String status, long count) {}

    public record RevenueAnalyticsDto(List<DailyAmount> dailyRevenueLast30Days, BigDecimal totalRefunded) {}

    public record RoutePopularity(String sourceCode, String destinationCode, long bookingCount) {}
    public record RoutesAnalyticsDto(List<RoutePopularity> topRoutes) {}

    public record CancellationsAnalyticsDto(
            long totalCancellations, BigDecimal totalRefundAmount, double averageDeductionPercent
    ) {}

    public record AiAnalyticsDto(
            long totalConversations, long totalMessages, long totalToolExecutions,
            long successfulToolExecutions, long failedToolExecutions
    ) {}

    public record RagAnalyticsDto(
            long totalQueries, long groundedQueries, long insufficientInfoQueries, Double averageTopSimilarity
    ) {}

    public record ToolBreakdown(String toolName, long callCount, long successCount, long failureCount) {}
    public record ToolsAnalyticsDto(List<ToolBreakdown> tools) {}
}
