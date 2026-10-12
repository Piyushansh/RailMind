package com.railmind.admin;

import com.railmind.admin.dto.AnalyticsDtos.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only analytics, all backed by real aggregation queries in
 * AnalyticsService - see that class for why native SQL rather than JPQL.
 * No endpoint here returns a hardcoded or synthetic number.
 */
@RestController
@RequestMapping("/api/v1/admin/analytics")
@PreAuthorize("hasRole('ADMIN')")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping
    public OverviewDto overview() {
        return analyticsService.overview();
    }

    @GetMapping("/bookings")
    public BookingsAnalyticsDto bookings() {
        return analyticsService.bookings();
    }

    @GetMapping("/revenue")
    public RevenueAnalyticsDto revenue() {
        return analyticsService.revenue();
    }

    @GetMapping("/routes")
    public RoutesAnalyticsDto routes() {
        return analyticsService.routes();
    }

    @GetMapping("/cancellations")
    public CancellationsAnalyticsDto cancellations() {
        return analyticsService.cancellations();
    }

    @GetMapping("/ai")
    public AiAnalyticsDto ai() {
        return analyticsService.ai();
    }

    @GetMapping("/rag")
    public RagAnalyticsDto rag() {
        return analyticsService.rag();
    }

    @GetMapping("/tools")
    public ToolsAnalyticsDto tools() {
        return analyticsService.tools();
    }
}
