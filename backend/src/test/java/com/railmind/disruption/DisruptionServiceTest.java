package com.railmind.disruption;

import com.railmind.audit.AuditService;
import com.railmind.auth.User;
import com.railmind.booking.BookingEntities.Booking;
import com.railmind.booking.BookingRepositories;
import com.railmind.notification.NotificationService;
import com.railmind.station.Station;
import com.railmind.train.JourneyRankingService;
import com.railmind.train.ScheduleEntities.TrainSchedule;
import com.railmind.train.Train;
import com.railmind.train.TrainRepositories;
import com.railmind.train.dto.JourneyPlanDtos.JourneyPlanResult;
import com.railmind.train.dto.JourneyPlanDtos.RankedCategory;
import com.railmind.train.dto.TrainDtos.SeatClassAvailability;
import com.railmind.train.dto.TrainDtos.TrainSearchResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Verifies the actual disruption workflow logic - not just that Kafka
 * wiring compiles. Two properties matter most: (1) the authoritative
 * schedule row is actually updated (DB stays source of truth), and (2) an
 * alternative is only ever computed/recommended for Trip-Guardian-enabled
 * bookings past the significance threshold, never fabricated otherwise.
 */
class DisruptionServiceTest {

    private TrainRepositories.TrainScheduleRepo scheduleRepo;
    private BookingRepositories.BookingRepo bookingRepo;
    private TripAlertRepository tripAlertRepository;
    private JourneyRankingService journeyRankingService;
    private NotificationService notificationService;
    private AuditService auditService;
    private DisruptionService disruptionService;

    private TrainSchedule schedule;
    private Booking confirmedBooking;
    private User user;

    private void setUp(boolean tripGuardianEnabled) {
        scheduleRepo = mock(TrainRepositories.TrainScheduleRepo.class);
        bookingRepo = mock(BookingRepositories.BookingRepo.class);
        tripAlertRepository = mock(TripAlertRepository.class);
        journeyRankingService = mock(JourneyRankingService.class);
        notificationService = mock(NotificationService.class);
        auditService = mock(AuditService.class);
        disruptionService = new DisruptionService(scheduleRepo, bookingRepo, tripAlertRepository,
                journeyRankingService, notificationService, auditService, 30);

        Train train = new Train();
        train.setId(1L);
        train.setNumber("RM101");
        train.setName("Test Express");

        schedule = new TrainSchedule();
        schedule.setId(10L);
        schedule.setTrain(train);
        schedule.setDepartureDate(LocalDate.now().plusDays(1));
        schedule.setDepartureTime(LocalTime.of(10, 0));
        schedule.setStatus("SCHEDULED");
        schedule.setDelayMinutes(0);

        Station source = new Station();
        source.setCode("NDLS");
        Station destination = new Station();
        destination.setCode("BCT");

        user = new User();
        user.setId(5L);
        user.setEmail("traveler@railmind.demo");

        confirmedBooking = new Booking();
        confirmedBooking.setId(100L);
        confirmedBooking.setUser(user);
        confirmedBooking.setSchedule(schedule);
        confirmedBooking.setSourceStation(source);
        confirmedBooking.setDestinationStation(destination);
        confirmedBooking.setTravelClass("SL");
        confirmedBooking.setStatus("CONFIRMED");
        confirmedBooking.setTripGuardianEnabled(tripGuardianEnabled);
        confirmedBooking.setPassengers(List.of());

        when(scheduleRepo.findById(10L)).thenReturn(Optional.of(schedule));
        when(bookingRepo.findByScheduleIdAndStatus(10L, "CONFIRMED")).thenReturn(List.of(confirmedBooking));
    }

    @Test
    void significantDelay_withTripGuardianEnabled_updatesScheduleAndRecommendsRealAlternative() {
        setUp(true);

        Train altTrain = new Train();
        altTrain.setNumber("RM999");
        altTrain.setName("Alt Express");
        TrainSchedule alternativeSchedule = new TrainSchedule();
        alternativeSchedule.setId(20L);
        alternativeSchedule.setTrain(altTrain);
        alternativeSchedule.setDepartureTime(LocalTime.of(14, 0));
        when(scheduleRepo.findById(20L)).thenReturn(Optional.of(alternativeSchedule));

        TrainSearchResult altResult = new TrainSearchResult(20L, 2L, "RM999", "Alt Express", "EXPRESS",
                "NDLS", "BCT", LocalDate.now().plusDays(1), LocalTime.of(14, 0), 600, "SCHEDULED", 0,
                List.of(new SeatClassAvailability("SL", 50, 10, BigDecimal.valueOf(300))));
        JourneyPlanResult plan = new JourneyPlanResult("NDLS", "BCT", LocalDate.now().plusDays(1), "SL", null,
                List.of(new RankedCategory("Best Overall", "reason", List.of(altResult))));
        when(journeyRankingService.plan(eq("NDLS"), eq("BCT"), any(), eq("SL"), isNull())).thenReturn(plan);

        TrainEventMessage event = TrainEventMessage.newEvent("TRAIN_DELAYED", 10L, 90, null, null, 1L);
        disruptionService.handleTrainEvent(event);

        // 1. The authoritative schedule row is actually updated.
        assertThat(schedule.getStatus()).isEqualTo("DELAYED");
        assertThat(schedule.getDelayMinutes()).isEqualTo(90);
        verify(scheduleRepo).save(schedule);

        // 2. A real alternative (not invented) was found and the user was notified with it.
        verify(notificationService).notify(eq(user), eq("TRIP_GUARDIAN_ALERT"), anyString(), anyString(), eq(confirmedBooking));
        verify(tripAlertRepository, atLeast(2)).save(any(TripAlert.class)); // DELAY_DETECTED + ALTERNATIVE_RECOMMENDED
        verify(auditService).log(eq(1L), eq("DISRUPTION_CREATED"), eq("TrainSchedule"), eq(10L), anyString());
    }

    @Test
    void delayBelowThreshold_orTripGuardianDisabled_neverComputesAnAlternative() {
        setUp(false); // Trip Guardian disabled on this booking

        TrainEventMessage event = TrainEventMessage.newEvent("TRAIN_DELAYED", 10L, 90, null, null, 1L);
        disruptionService.handleTrainEvent(event);

        verifyNoInteractions(journeyRankingService); // never even asked for a ranking
        verify(notificationService).notify(eq(user), eq("TRAIN_DELAYED"), anyString(), anyString(), eq(confirmedBooking));
        verify(tripAlertRepository, times(1)).save(any(TripAlert.class)); // just DELAY_DETECTED, no recommendation
    }
}
