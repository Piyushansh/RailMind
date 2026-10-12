package com.railmind.cancellation.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

public class CancellationDtos {

    public record CancelRequest(
            @NotNull Long bookingId,
            String reason
    ) {}

    public record RefundDto(
            Long bookingId,
            String pnrNumber,
            BigDecimal originalFare,
            BigDecimal deductionPercent,
            BigDecimal refundAmount,
            String explanation,
            String status,
            Instant processedAt
    ) {}
}
