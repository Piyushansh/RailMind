package com.railmind.payment;

import com.railmind.booking.BookingEntities.Booking;
import com.railmind.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String status = "PENDING"; // PENDING, SUCCESS, FAILED

    @Column(nullable = false)
    private String provider = "MOCK";

    @Column(name = "provider_reference")
    private String providerReference;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;
}
