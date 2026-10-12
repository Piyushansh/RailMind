package com.railmind.admin;

import com.railmind.admin.dto.AnalyticsDtos.OverviewDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves AnalyticsService computes REAL numbers from REAL rows, not that the
 * SQL merely runs without error. Inserts known bookings/payments/refunds
 * directly (against a fresh Testcontainers Postgres seeded only with the
 * demo stations/trains/schedules from V2, no bookings yet), then asserts the
 * aggregated result matches hand-computed expected values exactly.
 */
@Testcontainers
@SpringBootTest
class AnalyticsServiceIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("railmind").withUsername("railmind").withPassword("railmind");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("railmind.jwt.secret", () -> "test-only-secret-test-only-secret-32b");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired AnalyticsService analyticsService;

    @Test
    void overview_reflectsExactlyWhatWasInserted_notAnyHardcodedValue() {
        Long userId = jdbc.queryForObject(
                "INSERT INTO users (full_name, email, password_hash, roles) VALUES (?,?,?,?) RETURNING id",
                Long.class, "Analytics Test User", "analytics-test@railmind.demo", "x", "USER");

        Long scheduleId = jdbc.queryForObject(
                "SELECT ts.id FROM train_schedules ts JOIN trains t ON t.id = ts.train_id " +
                        "WHERE t.number = 'RM101' ORDER BY ts.departure_date LIMIT 1", Long.class);
        Long ndls = jdbc.queryForObject("SELECT id FROM stations WHERE code = 'NDLS'", Long.class);
        Long bct = jdbc.queryForObject("SELECT id FROM stations WHERE code = 'BCT'", Long.class);

        // One CONFIRMED booking with a successful payment of 500.00
        Long confirmedBookingId = jdbc.queryForObject("""
                INSERT INTO bookings (user_id, schedule_id, source_station_id, destination_station_id,
                                       travel_class, status, total_fare, idempotency_key)
                VALUES (?,?,?,?, 'SL', 'CONFIRMED', 500.00, ?) RETURNING id
                """, Long.class, userId, scheduleId, ndls, bct, "analytics-test-key-1");
        jdbc.update("""
                INSERT INTO payments (booking_id, amount, status, idempotency_key)
                VALUES (?, 500.00, 'SUCCESS', 'analytics-test-payment-1')
                """, confirmedBookingId);

        // One CANCELLED booking with a refund of 100.00 (20% deduction on a 125.00 fare)
        Long cancelledBookingId = jdbc.queryForObject("""
                INSERT INTO bookings (user_id, schedule_id, source_station_id, destination_station_id,
                                       travel_class, status, total_fare, idempotency_key)
                VALUES (?,?,?,?, 'SL', 'CANCELLED', 125.00, ?) RETURNING id
                """, Long.class, userId, scheduleId, ndls, bct, "analytics-test-key-2");
        Long cancellationId = jdbc.queryForObject("""
                INSERT INTO cancellations (booking_id, cancelled_by_user_id, hours_before_departure)
                VALUES (?, ?, 10.0) RETURNING id
                """, Long.class, cancelledBookingId, userId);
        jdbc.update("""
                INSERT INTO refunds (cancellation_id, original_fare, deduction_percent, refund_amount)
                VALUES (?, 125.00, 20.00, 100.00)
                """, cancellationId);

        OverviewDto overview = analyticsService.overview();

        assertThat(overview.totalBookings()).isEqualTo(2);
        assertThat(overview.confirmedBookings()).isEqualTo(1);
        assertThat(overview.cancelledBookings()).isEqualTo(1);
        assertThat(overview.totalRevenue()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(overview.totalRefunded()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(overview.cancellationRate()).isEqualTo(50.0);
    }
}
