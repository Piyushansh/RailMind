package com.railmind.disruption.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalTime;

public class DisruptionDtos {

    public record SimulateEventRequest(
            @NotNull Long scheduleId,
            @NotBlank String eventType,      // TRAIN_DELAYED, TRAIN_CANCELLED, PLATFORM_CHANGED, SCHEDULE_CHANGED
            Integer delayMinutes,            // required for TRAIN_DELAYED
            String newPlatform,              // required for PLATFORM_CHANGED
            LocalTime newDepartureTime       // required for SCHEDULE_CHANGED
    ) {}

    public record TripAlertDto(
            Long id,
            Long bookingId,
            String alertType,
            String message,
            Long suggestedAlternativeScheduleId,
            boolean resolved,
            Instant createdAt
    ) {}

    /**
     * Live status of the train a booking is on - status/delay/platform come straight off
     * TrainSchedule (the same fields AdminScheduleController lets an admin edit / the disruption
     * pipeline updates); estimated arrival is derived from the destination route stop's
     * arrivalOffsetMinutes plus any current delay, since there's no separate "arrival time"
     * column - a train's schedule is defined by one departure time plus per-stop offsets.
     */
    public record TripStatusDto(
            Long scheduleId,
            String trainNumber,
            String trainName,
            String status,
            int delayMinutes,
            String platform,
            java.time.LocalDate scheduledDepartureDate,
            LocalTime scheduledDepartureTime,
            LocalTime estimatedDepartureTime,
            LocalTime estimatedArrivalTime
    ) {}
}
