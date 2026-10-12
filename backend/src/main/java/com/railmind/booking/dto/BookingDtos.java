package com.railmind.booking.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class BookingDtos {

    public record PassengerRequest(
            @NotBlank String fullName,
            @Min(0) @Max(120) int age,
            @NotBlank String gender,
            @Pattern(regexp = "LOWER|MIDDLE|UPPER|SIDE_LOWER|SIDE_UPPER", message = "must be one of LOWER, MIDDLE, UPPER, SIDE_LOWER, SIDE_UPPER")
            String berthPreference,
            /** Optional: reference a saved passenger instead of repeating their details. When set, fullName/age/gender above are ignored and the saved record's values are used, after verifying it belongs to the requesting user. */
            Long savedPassengerId
    ) {}

    public record CreateBookingRequest(
            @NotNull Long scheduleId,
            @NotNull Long sourceStationId,
            @NotNull Long destinationStationId,
            /** Must match a class actually configured on coaches/fares - see V2__seed_data.sql. */
            @NotBlank @Pattern(regexp = "SL|AC3|AC2|AC1", message = "must be one of SL, AC3, AC2, AC1")
            String travelClass,
            /** Max 6 passengers per booking - standard Indian-rail-style booking limit (spec section 70: "Cannot exceed passenger limits"). */
            @NotEmpty @Size(max = 6, message = "a single booking cannot include more than 6 passengers")
            @Valid List<PassengerRequest> passengers,
            /** Client-generated key so a retried request never creates a duplicate booking. */
            @NotBlank String idempotencyKey
    ) {}

    public record PassengerAllocation(String fullName, int age, String gender, String coachCode, String seatNumber, String berthType) {}

    public record BookingDto(
            Long bookingId,
            Long scheduleId,
            String status,
            String trainNumber,
            String trainName,
            String sourceStationCode,
            String destinationStationCode,
            String travelClass,
            BigDecimal totalFare,
            List<PassengerAllocation> passengers,
            String pnrNumber,
            boolean tripGuardianEnabled,
            Instant createdAt
    ) {}
}
