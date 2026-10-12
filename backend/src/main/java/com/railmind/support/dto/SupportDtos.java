package com.railmind.support.dto;

import com.railmind.booking.dto.BookingDtos.BookingDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * All support-facing shapes live here, deliberately separate from the admin
 * DTOs even where the underlying data overlaps (e.g. bookings) - support and
 * admin have different visibility rules (support never sees a user's
 * password/tokens, never sees other staff accounts, cannot mutate anything),
 * and keeping the DTOs separate makes it obvious at a glance which fields a
 * support agent is actually allowed to see.
 */
public class SupportDtos {

    public record PaymentSummary(String status, BigDecimal amount, String provider, Instant createdAt) {}

    public record CancellationSummary(String reason, BigDecimal hoursBeforeDeparture, Instant cancelledAt,
                                       BigDecimal refundAmount, BigDecimal deductionPercent, String refundStatus) {}

    public record DisruptionSummary(String alertType, String message, boolean resolved, Instant createdAt) {}

    public record TimelineEvent(String label, Instant occurredAt) {}

    /**
     * Everything a support agent is allowed to see about one booking, in one
     * call, so the frontend doesn't have to stitch together 4 separate
     * requests (booking + payment + cancellation + disruptions) itself.
     * Never includes the customer's password hash, refresh/reset tokens, or
     * any other account secret - those simply aren't on this DTO at all.
     */
    public record SupportBookingDetailDto(
            BookingDto booking,
            String customerEmail,
            PaymentSummary payment,
            CancellationSummary cancellation,
            List<DisruptionSummary> disruptions,
            List<TimelineEvent> timeline
    ) {}

    /** Deliberately excludes email/phone in list results beyond what's needed to disambiguate -
     * full detail is only shown once an agent opens a specific customer, itself an audited action. */
    public record CustomerSearchResultDto(Long userId, String fullName, String email, boolean enabled) {}

    public record AiActionDto(Long conversationId, String toolName, boolean success, Instant createdAt) {}
}
