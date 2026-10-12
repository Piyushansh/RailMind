package com.railmind.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class PaymentDtos {

    public record PayRequest(
            @NotNull Long bookingId,
            @NotBlank String idempotencyKey
    ) {}

    public record PaymentResultDto(
            Long paymentId,
            Long bookingId,
            String status,
            String bookingStatus,
            String pnrNumber,
            String failureReason
    ) {}
}
