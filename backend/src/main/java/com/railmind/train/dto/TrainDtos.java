package com.railmind.train.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public class TrainDtos {

    public record SeatClassAvailability(String travelClass, int totalSeats, int availableSeats, BigDecimal fare) {}

    public record TrainSearchResult(
            Long scheduleId,
            Long trainId,
            String trainNumber,
            String trainName,
            String trainType,
            String sourceStationCode,
            String destinationStationCode,
            LocalDate departureDate,
            LocalTime departureTime,
            int durationMinutes,
            String status,
            int delayMinutes,
            List<SeatClassAvailability> classes
    ) {}

    public record TrainDetailsDto(
            Long trainId,
            String trainNumber,
            String trainName,
            String trainType,
            List<RouteStop> route
    ) {}

    public record RouteStop(String stationCode, String stationName, int sequenceNo,
                             int arrivalOffsetMinutes, int departureOffsetMinutes, int distanceKm) {}

    public record AvailabilityDto(Long scheduleId, String travelClass, int totalSeats, int availableSeats) {}

    public record FareQuoteDto(Long trainId, String travelClass, int distanceKm,
                                BigDecimal baseFare, BigDecimal perKmRate, BigDecimal totalFarePerPassenger) {}
}
