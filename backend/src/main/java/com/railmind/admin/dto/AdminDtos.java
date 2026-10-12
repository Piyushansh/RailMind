package com.railmind.admin.dto;

import jakarta.validation.constraints.NotBlank;

public class AdminDtos {

    public record CreateStationRequest(
            @NotBlank String code,
            @NotBlank String name,
            @NotBlank String city,
            String state
    ) {}

    public record UpdateStationRequest(
            @NotBlank String name,
            @NotBlank String city,
            String state
    ) {}

    public record CreateTrainRequest(
            @NotBlank String number,
            @NotBlank String name,
            @NotBlank String trainType,
            @NotBlank String sourceStationCode,
            @NotBlank String destinationStationCode
    ) {}

    public record UpdateTrainRequest(
            @NotBlank String name,
            @NotBlank String trainType
    ) {}

    // ---- Full train-provisioning wizard (spec: "creating a train must support route,
    // schedule, coaches, seats, fare classes - do not leave train records unusable") ----

    public record RouteStopRequest(
            @NotBlank String stationCode,
            @jakarta.validation.constraints.NotNull Integer sequenceNo,
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Min(0) Integer arrivalOffsetMinutes,
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Min(0) Integer departureOffsetMinutes,
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Min(0) Integer distanceKm
    ) {}

    public record CoachClassRequest(
            @jakarta.validation.constraints.Pattern(regexp = "SL|AC3|AC2|AC1", message = "must be one of SL, AC3, AC2, AC1")
            String travelClass,
            @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(10) int coachCount,
            @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(120) int seatsPerCoach,
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Min(0) java.math.BigDecimal baseFare,
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Min(0) java.math.BigDecimal perKmRate
    ) {}

    public record CreateFullTrainRequest(
            @NotBlank String number,
            @NotBlank String name,
            @NotBlank String trainType,
            @NotBlank String sourceStationCode,
            @NotBlank String destinationStationCode,
            /** Intermediate stops only - source (seq 1, offsets 0) and destination (last seq) are added automatically. */
            java.util.List<RouteStopRequest> intermediateStops,
            @jakarta.validation.constraints.NotEmpty @jakarta.validation.Valid
            java.util.List<CoachClassRequest> coachClasses,
            @jakarta.validation.constraints.NotNull java.time.LocalTime departureTime,
            /** How many consecutive days (starting tomorrow) to generate a daily schedule for. */
            @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(90) int scheduleDays
    ) {}

    public record ActivationRequest(@jakarta.validation.constraints.NotNull Boolean active) {}

    public record PageResponse<T>(java.util.List<T> content, int page, int size, long totalElements, int totalPages) {
        public static <T> PageResponse<T> from(org.springframework.data.domain.Page<T> p) {
            return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
        }
    }
}
