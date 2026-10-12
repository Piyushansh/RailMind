package com.railmind.disruption;

import com.railmind.auth.CurrentUser;
import com.railmind.booking.BookingEntities.Booking;
import com.railmind.booking.BookingService;
import com.railmind.booking.dto.BookingDtos.BookingDto;
import com.railmind.disruption.dto.DisruptionDtos.TripAlertDto;
import com.railmind.disruption.dto.DisruptionDtos.TripStatusDto;
import com.railmind.train.ScheduleEntities.TrainSchedule;
import com.railmind.train.TrainRepositories.RouteRepo;
import com.railmind.train.TrainRepositories.RouteStationRepo;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bookings/{bookingId}")
public class TripGuardianController {

    private final BookingService bookingService;
    private final RebookingService rebookingService;
    private final TripAlertRepository tripAlertRepository;
    private final RouteRepo routeRepo;
    private final RouteStationRepo routeStationRepo;
    private final CurrentUser currentUser;

    public TripGuardianController(BookingService bookingService, RebookingService rebookingService,
                                   TripAlertRepository tripAlertRepository, RouteRepo routeRepo,
                                   RouteStationRepo routeStationRepo, CurrentUser currentUser) {
        this.bookingService = bookingService;
        this.rebookingService = rebookingService;
        this.tripAlertRepository = tripAlertRepository;
        this.routeRepo = routeRepo;
        this.routeStationRepo = routeStationRepo;
        this.currentUser = currentUser;
    }

    public record TripGuardianToggleRequest(@NotNull Boolean enabled) {}

    @PostMapping("/trip-guardian")
    public BookingDto setTripGuardian(@PathVariable Long bookingId, @RequestBody TripGuardianToggleRequest request,
                                       Authentication auth) {
        return bookingService.setTripGuardianEnabled(bookingId, currentUser.resolveId(auth), request.enabled());
    }

    @GetMapping("/trip-status")
    public TripStatusDto getTripStatus(@PathVariable Long bookingId, Authentication auth) {
        Long userId = currentUser.resolveId(auth);
        Booking booking = bookingService.getRawEntity(bookingId, userId); // ownership check
        TrainSchedule schedule = booking.getSchedule();
        int delayMinutes = schedule.getDelayMinutes() != null ? schedule.getDelayMinutes() : 0;

        // Estimated arrival = scheduled departure + delay + the destination stop's arrival
        // offset. Deliberately LocalTime (not a full date-aware Instant): this project's
        // schedules are defined as one departure time plus per-stop minute offsets, with no
        // separate "arrival" column, and demo journeys stay within a day or two - a multi-day
        // journey would need date-aware arithmetic this simplification doesn't attempt.
        java.time.LocalTime estimatedDeparture = schedule.getDepartureTime().plusMinutes(delayMinutes);
        java.time.LocalTime estimatedArrival = routeRepo.findByTrainId(schedule.getTrain().getId())
                .map(route -> routeStationRepo.findByRouteIdOrderBySequenceNoAsc(route.getId()))
                .filter(stops -> !stops.isEmpty())
                .map(stops -> stops.get(stops.size() - 1))
                .map(destinationStop -> schedule.getDepartureTime()
                        .plusMinutes(destinationStop.getArrivalOffsetMinutes() + delayMinutes))
                .orElse(estimatedDeparture);

        return new TripStatusDto(schedule.getId(), schedule.getTrain().getNumber(), schedule.getTrain().getName(),
                schedule.getStatus(), delayMinutes, schedule.getPlatform(), schedule.getDepartureDate(),
                schedule.getDepartureTime(), estimatedDeparture, estimatedArrival);
    }

    @GetMapping("/trip-alerts")
    public List<TripAlertDto> getTripAlerts(@PathVariable Long bookingId, Authentication auth) {
        Long userId = currentUser.resolveId(auth);
        // Ownership check: this throws if the booking isn't the caller's own, before we ever touch alerts.
        bookingService.getRawEntity(bookingId, userId);
        return tripAlertRepository.findByBookingIdOrderByCreatedAtDesc(bookingId).stream()
                .map(a -> new TripAlertDto(a.getId(), bookingId, a.getAlertType(), a.getMessage(),
                        a.getSuggestedAlternativeSchedule() != null ? a.getSuggestedAlternativeSchedule().getId() : null,
                        a.isResolved(), a.getCreatedAt()))
                .toList();
    }

    @PostMapping("/trip-alerts/{alertId}/approve-rebooking")
    public BookingDto approveRebooking(@PathVariable Long bookingId, @PathVariable Long alertId, Authentication auth) {
        return rebookingService.approveRebooking(currentUser.resolveId(auth), bookingId, alertId);
    }

    @PostMapping("/trip-alerts/{alertId}/reject-rebooking")
    public void rejectRebooking(@PathVariable Long bookingId, @PathVariable Long alertId, Authentication auth) {
        rebookingService.rejectRebooking(currentUser.resolveId(auth), bookingId, alertId);
    }
}
