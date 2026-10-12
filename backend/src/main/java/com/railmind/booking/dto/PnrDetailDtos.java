package com.railmind.booking.dto;

import com.railmind.booking.dto.BookingDtos.BookingDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The full user-facing view for /pnr and the booking detail page - BookingDto
 * alone doesn't carry payment/cancellation/refund/disruption status, and
 * repeating that composition logic inline in a controller would duplicate
 * what SupportDtos.SupportBookingDetailDto already does for support. This is
 * a deliberately separate (not shared) type: support's version can show a
 * user's booking to a DIFFERENT person (an agent) and is composed by
 * SupportService; this one is always ownership-scoped to the caller and
 * composed by PnrDetailService, kept independent so a future change to one
 * audience's shape can't accidentally leak into the other's.
 */
public class PnrDetailDtos {

    public record PaymentStatus(String status, BigDecimal amount, Instant paidAt) {}

    public record CancellationStatus(String reason, Instant cancelledAt) {}

    public record RefundStatus(BigDecimal originalFare, BigDecimal deductionPercent,
                                BigDecimal refundAmount, String status, Instant processedAt) {}

    public record DisruptionStatus(String alertType, String message, boolean resolved, Instant createdAt) {}

    public record PnrDetailDto(
            BookingDto booking,
            PaymentStatus payment,
            CancellationStatus cancellation,
            RefundStatus refund,
            List<DisruptionStatus> disruptions
    ) {}
}
