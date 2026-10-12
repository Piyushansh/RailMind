package com.railmind.booking;

import com.railmind.audit.AuditService;
import com.railmind.auth.User;
import com.railmind.auth.UserRepository;
import com.railmind.common.exceptions.AppExceptions.ConflictException;
import com.railmind.station.Station;
import com.railmind.station.StationRepository;
import com.railmind.train.ScheduleEntities.TrainSchedule;
import com.railmind.train.Train;
import com.railmind.train.TrainRepositories;
import com.railmind.train.TrainSearchService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression test for spec section 70's "Cannot book in the past" rule, added
 * this pass: BookingService.createBooking must reject a schedule whose
 * original departure date/time has already elapsed, even if its status is
 * still SCHEDULED (e.g. seed/demo data that was never rolled forward).
 * Hand-wired unit test, no Spring context / DB, mirrors
 * BookingIdempotencyOwnershipTest's style.
 */
class BookingPastDepartureRuleTest {

    @Test
    void cannotBookASchedule_whoseDepartureHasAlreadyElapsed() {
        BookingRepositories.BookingRepo bookingRepo = mock(BookingRepositories.BookingRepo.class);
        BookingRepositories.PassengerRepo passengerRepo = mock(BookingRepositories.PassengerRepo.class);
        BookingRepositories.BookingPassengerRepo bpRepo = mock(BookingRepositories.BookingPassengerRepo.class);
        BookingRepositories.PnrRepo pnrRepo = mock(BookingRepositories.PnrRepo.class);
        TrainRepositories.TrainScheduleRepo scheduleRepo = mock(TrainRepositories.TrainScheduleRepo.class);
        TrainRepositories.SeatAvailabilityRepo seatAvailabilityRepo = mock(TrainRepositories.SeatAvailabilityRepo.class);
        StationRepository stationRepository = mock(StationRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        TrainSearchService trainSearchService = mock(TrainSearchService.class);
        AuditService auditService = mock(AuditService.class);

        BookingService bookingService = new BookingService(bookingRepo, passengerRepo, bpRepo, pnrRepo,
                scheduleRepo, seatAvailabilityRepo, stationRepository, userRepository, trainSearchService, auditService, 15L);

        User user = new User();
        user.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(bookingRepo.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());

        Train train = new Train();
        train.setId(10L);

        TrainSchedule pastSchedule = new TrainSchedule();
        pastSchedule.setId(100L);
        pastSchedule.setTrain(train);
        pastSchedule.setStatus("SCHEDULED");
        // Yesterday: already departed, regardless of status still saying SCHEDULED.
        pastSchedule.setDepartureDate(LocalDate.now().minusDays(1));
        pastSchedule.setDepartureTime(LocalTime.of(10, 0));

        when(scheduleRepo.findById(100L)).thenReturn(Optional.of(pastSchedule));

        Station source = new Station();
        source.setId(1L);
        Station destination = new Station();
        destination.setId(2L);
        when(stationRepository.findById(1L)).thenReturn(Optional.of(source));
        when(stationRepository.findById(2L)).thenReturn(Optional.of(destination));

        var req = new com.railmind.booking.dto.BookingDtos.CreateBookingRequest(
                100L, 1L, 2L, "SL", java.util.List.of(), "key-1");

        assertThatThrownBy(() -> bookingService.createBooking(1L, req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already departed");
    }
}
