package com.railmind.disruption;

import com.railmind.audit.AuditService;
import com.railmind.booking.BookingEntities.Booking;
import com.railmind.booking.BookingRepositories;
import com.railmind.common.exceptions.AppExceptions.BadRequestException;
import com.railmind.common.exceptions.AppExceptions.ResourceNotFoundException;
import com.railmind.notification.NotificationService;
import com.railmind.train.ScheduleEntities.TrainSchedule;
import com.railmind.train.TrainRepositories;
import com.railmind.train.dto.JourneyPlanDtos.JourneyPlanResult;
import com.railmind.train.dto.JourneyPlanDtos.RankedCategory;
import com.railmind.train.dto.TrainDtos.TrainSearchResult;
import com.railmind.train.JourneyRankingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * The core disruption workflow, called by TrainEventConsumer (async, off a
 * Kafka event) and reachable synchronously too if ever needed - all the
 * actual logic lives here rather than in the Kafka listener, per "Kafka
 * should trigger asynchronous workflows around the existing business
 * services, not contain duplicate business logic."
 *
 * For each event:
 * 1. Updates the authoritative train_schedules row (status/delay/platform/time) -
 *    the database remains the source of truth, Kafka is just the trigger.
 * 2. Finds every CONFIRMED booking on that schedule.
 * 3. Records a TripAlert for each (the Trip Guardian timeline).
 * 4. For Trip-Guardian-enabled bookings where the disruption is significant
 *    (cancelled, or delayed past the configured threshold), searches real
 *    alternatives via TrainSearchService/JourneyRankingService and attaches
 *    the top "Best Overall" pick to the alert - never inventing one.
 * 5. Notifies the affected passenger either way.
 */
@Service
public class DisruptionService {

    private static final Logger log = LoggerFactory.getLogger(DisruptionService.class);

    private final TrainRepositories.TrainScheduleRepo scheduleRepo;
    private final BookingRepositories.BookingRepo bookingRepo;
    private final TripAlertRepository tripAlertRepository;
    private final JourneyRankingService journeyRankingService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final int delayThresholdMinutes;

    public DisruptionService(TrainRepositories.TrainScheduleRepo scheduleRepo,
                              BookingRepositories.BookingRepo bookingRepo,
                              TripAlertRepository tripAlertRepository,
                              JourneyRankingService journeyRankingService,
                              NotificationService notificationService,
                              AuditService auditService,
                              @Value("${railmind.trip-guardian.alternative-suggestion-delay-threshold-minutes}") int delayThresholdMinutes) {
        this.scheduleRepo = scheduleRepo;
        this.bookingRepo = bookingRepo;
        this.tripAlertRepository = tripAlertRepository;
        this.journeyRankingService = journeyRankingService;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.delayThresholdMinutes = delayThresholdMinutes;
    }

    @Transactional
    public void handleTrainEvent(TrainEventMessage event) {
        TrainSchedule schedule = scheduleRepo.findById(event.scheduleId())
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found: " + event.scheduleId()));

        String humanMessage = applyEventToSchedule(schedule, event);
        scheduleRepo.save(schedule);

        auditService.log(event.triggeredByUserId(), "DISRUPTION_CREATED", "TrainSchedule", schedule.getId(),
                event.eventType() + ": " + humanMessage);

        List<Booking> affected = bookingRepo.findByScheduleIdAndStatus(schedule.getId(), "CONFIRMED");
        boolean significant = "TRAIN_CANCELLED".equals(event.eventType())
                || (event.delayMinutes() != null && event.delayMinutes() >= delayThresholdMinutes);

        for (Booking booking : affected) {
            TrainSchedule alternative = significant && booking.isTripGuardianEnabled()
                    ? findBestAlternative(booking, schedule)
                    : null;

            TripAlert alert = new TripAlert();
            alert.setBooking(booking);
            alert.setAlertType(alertTypeFor(event.eventType()));
            alert.setSuggestedAlternativeSchedule(alternative);

            String alertMessage = humanMessage;
            if (alternative != null) {
                alertMessage += " A suggested alternative is available - review it in Trip Guardian.";
                TripAlert recommendationAlert = new TripAlert();
                recommendationAlert.setBooking(booking);
                recommendationAlert.setAlertType("ALTERNATIVE_RECOMMENDED");
                recommendationAlert.setMessage("Recommended alternative: train " + alternative.getTrain().getNumber()
                        + " (" + alternative.getTrain().getName() + ") departing " + alternative.getDepartureTime() + ".");
                recommendationAlert.setSuggestedAlternativeSchedule(alternative);
                tripAlertRepository.save(recommendationAlert);
            }
            alert.setMessage(alertMessage);
            tripAlertRepository.save(alert);

            notificationService.notify(booking.getUser(),
                    alternative != null ? "TRIP_GUARDIAN_ALERT" : event.eventType(),
                    titleFor(event.eventType()),
                    alertMessage,
                    booking);
        }

        log.info("Processed {} for schedule {}: {} affected confirmed booking(s), {} with an alternative recommended.",
                event.eventType(), schedule.getId(), affected.size(),
                affected.stream().filter(b -> b.isTripGuardianEnabled()).count());
    }

    private String applyEventToSchedule(TrainSchedule schedule, TrainEventMessage event) {
        return switch (event.eventType()) {
            case "TRAIN_DELAYED" -> {
                int minutes = requireNonNull(event.delayMinutes(), "delayMinutes is required for TRAIN_DELAYED");
                schedule.setStatus("DELAYED");
                schedule.setDelayMinutes(minutes);
                yield "Train " + schedule.getTrain().getNumber() + " is delayed by " + minutes + " minutes.";
            }
            case "TRAIN_CANCELLED" -> {
                schedule.setStatus("CANCELLED");
                yield "Train " + schedule.getTrain().getNumber() + " has been cancelled for this date.";
            }
            case "PLATFORM_CHANGED" -> {
                String platform = requireNonNull(event.newPlatform(), "newPlatform is required for PLATFORM_CHANGED");
                schedule.setPlatform(platform);
                yield "Platform changed to " + platform + " for train " + schedule.getTrain().getNumber() + ".";
            }
            case "SCHEDULE_CHANGED" -> {
                java.time.LocalTime newTime = event.newDepartureTime() != null
                        ? java.time.LocalTime.parse(event.newDepartureTime())
                        : null;
                if (newTime == null) throw new BadRequestException("newDepartureTime is required for SCHEDULE_CHANGED");
                schedule.setDepartureTime(newTime);
                schedule.setStatus("SCHEDULE_CHANGED");
                yield "Departure time for train " + schedule.getTrain().getNumber() + " changed to " + newTime + ".";
            }
            default -> throw new BadRequestException("Unknown disruption event type: " + event.eventType());
        };
    }

    private TrainSchedule findBestAlternative(Booking booking, TrainSchedule disruptedSchedule) {
        try {
            JourneyPlanResult plan = journeyRankingService.plan(
                    booking.getSourceStation().getCode(), booking.getDestinationStation().getCode(),
                    disruptedSchedule.getDepartureDate(), booking.getTravelClass(), null);

            Optional<TrainSearchResult> best = plan.categories().stream()
                    .filter(c -> c.category().equals("Best Overall"))
                    .flatMap(c -> c.options().stream())
                    .filter(r -> !r.scheduleId().equals(disruptedSchedule.getId()))
                    .findFirst();

            return best.map(r -> scheduleRepo.findById(r.scheduleId()).orElse(null)).orElse(null);
        } catch (Exception e) {
            log.warn("Could not compute an alternative for booking {}: {}", booking.getId(), e.getMessage());
            return null;
        }
    }

    private String alertTypeFor(String eventType) {
        return switch (eventType) {
            case "TRAIN_DELAYED" -> "DELAY_DETECTED";
            case "TRAIN_CANCELLED" -> "CANCELLATION_DETECTED";
            case "PLATFORM_CHANGED" -> "PLATFORM_CHANGED";
            case "SCHEDULE_CHANGED" -> "SCHEDULE_CHANGED";
            default -> eventType;
        };
    }

    private String titleFor(String eventType) {
        return switch (eventType) {
            case "TRAIN_DELAYED" -> "Train delayed";
            case "TRAIN_CANCELLED" -> "Train cancelled";
            case "PLATFORM_CHANGED" -> "Platform changed";
            case "SCHEDULE_CHANGED" -> "Schedule changed";
            default -> "Trip update";
        };
    }

    private <T> T requireNonNull(T value, String message) {
        if (value == null) throw new BadRequestException(message);
        return value;
    }
}
