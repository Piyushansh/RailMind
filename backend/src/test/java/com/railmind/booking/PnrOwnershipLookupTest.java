package com.railmind.booking;

import com.railmind.audit.AuditService;
import com.railmind.auth.User;
import com.railmind.auth.UserRepository;
import com.railmind.booking.BookingEntities.Booking;
import com.railmind.booking.BookingEntities.Pnr;
import com.railmind.common.exceptions.AppExceptions.ResourceNotFoundException;
import com.railmind.station.StationRepository;
import com.railmind.train.TrainRepositories;
import com.railmind.train.TrainSearchService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression test for the new public PNR lookup (GET /api/v1/pnr/{pnr}):
 * must return the booking when it belongs to the caller, and must throw the
 * SAME 404 ResourceNotFoundException whether the PNR doesn't exist at all or
 * belongs to a different user - never a distinguishable 403/different
 * message, since that would let PNR ownership be enumerated.
 */
class PnrOwnershipLookupTest {

    private BookingService newService(BookingRepositories.PnrRepo pnrRepo) {
        return new BookingService(
                mock(BookingRepositories.BookingRepo.class),
                mock(BookingRepositories.PassengerRepo.class),
                mock(BookingRepositories.BookingPassengerRepo.class),
                pnrRepo,
                mock(TrainRepositories.TrainScheduleRepo.class),
                mock(TrainRepositories.SeatAvailabilityRepo.class),
                mock(StationRepository.class),
                mock(UserRepository.class),
                mock(TrainSearchService.class),
                mock(AuditService.class),
                15L);
    }

    @Test
    void ownPnr_returnsBooking() {
        BookingRepositories.PnrRepo pnrRepo = mock(BookingRepositories.PnrRepo.class);
        BookingService bookingService = newService(pnrRepo);

        User owner = new User();
        owner.setId(1L);

        Booking booking = bookingWithOwner(owner);

        Pnr pnr = new Pnr();
        pnr.setBooking(booking);
        pnr.setPnrNumber("1234567890");

        when(pnrRepo.findByPnrNumber("1234567890")).thenReturn(Optional.of(pnr));
        when(pnrRepo.findByBookingId(booking.getId())).thenReturn(Optional.of(pnr));

        var dto = bookingService.getBookingByPnr("1234567890", 1L);
        assertThat(dto.pnrNumber()).isEqualTo("1234567890");
    }

    @Test
    void pnrBelongingToAnotherUser_isNotFound_not403() {
        BookingRepositories.PnrRepo pnrRepo = mock(BookingRepositories.PnrRepo.class);
        BookingService bookingService = newService(pnrRepo);

        User someoneElse = new User();
        someoneElse.setId(999L);
        Booking booking = bookingWithOwner(someoneElse);

        Pnr pnr = new Pnr();
        pnr.setBooking(booking);
        pnr.setPnrNumber("1234567890");

        when(pnrRepo.findByPnrNumber("1234567890")).thenReturn(Optional.of(pnr));

        assertThatThrownBy(() -> bookingService.getBookingByPnr("1234567890", 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void nonExistentPnr_isNotFound() {
        BookingRepositories.PnrRepo pnrRepo = mock(BookingRepositories.PnrRepo.class);
        BookingService bookingService = newService(pnrRepo);

        when(pnrRepo.findByPnrNumber("0000000000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.getBookingByPnr("0000000000", 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private Booking bookingWithOwner(User owner) {
        Booking booking = new Booking();
        booking.setId(42L);
        booking.setUser(owner);
        booking.setPassengers(new java.util.ArrayList<>());

        com.railmind.train.ScheduleEntities.TrainSchedule schedule = new com.railmind.train.ScheduleEntities.TrainSchedule();
        com.railmind.train.Train train = new com.railmind.train.Train();
        train.setNumber("12345");
        train.setName("Test Express");
        schedule.setTrain(train);
        booking.setSchedule(schedule);

        com.railmind.station.Station source = new com.railmind.station.Station();
        source.setCode("NDLS");
        com.railmind.station.Station destination = new com.railmind.station.Station();
        destination.setCode("BCT");
        booking.setSourceStation(source);
        booking.setDestinationStation(destination);
        booking.setTravelClass("SL");
        booking.setTotalFare(java.math.BigDecimal.valueOf(500));
        booking.setStatus("CONFIRMED");
        return booking;
    }
}
