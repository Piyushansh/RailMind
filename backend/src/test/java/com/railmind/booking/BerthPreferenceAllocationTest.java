package com.railmind.booking;

import com.railmind.audit.AuditService;
import com.railmind.auth.User;
import com.railmind.auth.UserRepository;
import com.railmind.station.Station;
import com.railmind.station.StationRepository;
import com.railmind.train.ScheduleEntities.TrainSchedule;
import com.railmind.train.SeatEntities.Coach;
import com.railmind.train.SeatEntities.Seat;
import com.railmind.train.SeatEntities.SeatAvailability;
import com.railmind.train.Train;
import com.railmind.train.TrainRepositories;
import com.railmind.train.TrainSearchService;
import com.railmind.train.dto.TrainDtos.FareQuoteDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression test for a bug fixed this pass: BookingService previously stored
 * a passenger's berthPreference on the BookingPassenger record but never
 * actually used it to pick a seat - it always took the first AVAILABLE
 * candidate regardless of berth type. This confirms a matching berth is now
 * preferred when one is open, alongside a non-matching one.
 */
class BerthPreferenceAllocationTest {

    @Test
    void whenPreferredBerthIsAvailable_itIsChosenOverOtherOpenSeats() {
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
        when(bookingRepo.findByIdempotencyKey("key-berth")).thenReturn(Optional.empty());
        when(bookingRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Train train = new Train();
        train.setId(10L);

        TrainSchedule schedule = new TrainSchedule();
        schedule.setId(100L);
        schedule.setTrain(train);
        schedule.setStatus("SCHEDULED");
        schedule.setDepartureDate(LocalDate.now().plusDays(5));
        schedule.setDepartureTime(LocalTime.of(10, 0));
        when(scheduleRepo.findById(100L)).thenReturn(Optional.of(schedule));

        Station source = new Station();
        source.setId(1L);
        Station destination = new Station();
        destination.setId(2L);
        when(stationRepository.findById(1L)).thenReturn(Optional.of(source));
        when(stationRepository.findById(2L)).thenReturn(Optional.of(destination));

        when(trainSearchService.getRouteSpan(10L, 1L, 2L))
                .thenReturn(new TrainSearchService.RouteSpan(500, 480));
        when(trainSearchService.calculateFare(10L, "SL", 500))
                .thenReturn(new FareQuoteDto(10L, "SL", 500, BigDecimal.valueOf(150), BigDecimal.valueOf(0.55), BigDecimal.valueOf(425)));

        Coach coach = new Coach();
        coach.setId(1L);
        coach.setCoachCode("S1");
        coach.setTravelClass("SL");

        Seat lowerSeat = new Seat();
        lowerSeat.setId(1L);
        lowerSeat.setSeatNumber("1");
        lowerSeat.setBerthType("LOWER");
        lowerSeat.setCoach(coach);

        Seat upperSeat = new Seat();
        upperSeat.setId(2L);
        upperSeat.setSeatNumber("2");
        upperSeat.setBerthType("UPPER");
        upperSeat.setCoach(coach);

        SeatAvailability lowerAvailability = new SeatAvailability();
        lowerAvailability.setId(1L);
        lowerAvailability.setSeat(lowerSeat);
        lowerAvailability.setStatus("AVAILABLE");

        SeatAvailability upperAvailability = new SeatAvailability();
        upperAvailability.setId(2L);
        upperAvailability.setSeat(upperSeat);
        upperAvailability.setStatus("AVAILABLE");

        // Upper listed first, deliberately - if preference weren't honored, the old
        // implementation's candidates.get(0) would grab this one instead of the requested LOWER.
        when(seatAvailabilityRepo.findByScheduleIdAndSeat_Coach_TravelClassAndStatus(100L, "SL", "AVAILABLE"))
                .thenReturn(List.of(upperAvailability, lowerAvailability));
        when(seatAvailabilityRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var passengerReq = new com.railmind.booking.dto.BookingDtos.PassengerRequest(
                "Test Passenger", 30, "M", "LOWER", null);
        when(passengerRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var req = new com.railmind.booking.dto.BookingDtos.CreateBookingRequest(
                100L, 1L, 2L, "SL", List.of(passengerReq), "key-berth");

        var dto = bookingService.createBooking(1L, req);

        assertThat(dto.passengers()).hasSize(1);
        assertThat(dto.passengers().get(0).berthType()).isEqualTo("LOWER");
    }
}
