package com.railmind.cancellation;

import com.railmind.auth.User;
import com.railmind.booking.BookingEntities.Booking;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

public class CancellationEntities {

    @Getter @Setter
    @Entity
    @Table(name = "cancellations")
    public static class Cancellation {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "booking_id", nullable = false)
        private Booking booking;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "cancelled_by_user_id", nullable = false)
        private User cancelledBy;

        private String reason;

        @Column(name = "hours_before_departure", nullable = false)
        private BigDecimal hoursBeforeDeparture;

        @Column(name = "created_at", nullable = false, updatable = false)
        private Instant createdAt;

        @PrePersist
        void onCreate() { createdAt = Instant.now(); }
    }

    @Getter @Setter
    @Entity
    @Table(name = "refunds")
    public static class Refund {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @OneToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "cancellation_id", nullable = false, unique = true)
        private Cancellation cancellation;

        @Column(name = "original_fare", nullable = false)
        private BigDecimal originalFare;

        @Column(name = "deduction_percent", nullable = false)
        private BigDecimal deductionPercent;

        @Column(name = "refund_amount", nullable = false)
        private BigDecimal refundAmount;

        @Column(nullable = false)
        private String status = "PROCESSED";

        @Column(name = "created_at", nullable = false, updatable = false)
        private Instant createdAt;

        @PrePersist
        void onCreate() { createdAt = Instant.now(); }
    }
}
