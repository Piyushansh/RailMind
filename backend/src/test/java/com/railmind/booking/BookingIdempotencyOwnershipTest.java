package com.railmind.booking;

import com.railmind.audit.AuditService;
import com.railmind.auth.User;
import com.railmind.auth.UserRepository;
import com.railmind.booking.BookingEntities.Booking;
import com.railmind.common.exceptions.AppExceptions.ConflictException;
import com.railmind.station.StationRepository;
import com.railmind.train.TrainRepositories;
import com.railmind.train.TrainSearchService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression test for the IDOR fixed in this pass: a request whose idempotency
 * key already belongs to a DIFFERENT user must be rejected, never silently
 * handed the other user's booking. This is a pure unit test against a
 * hand-wired BookingService (no Spring context / DB needed) so it runs fast
 * and documents the exact contract.
 */
class BookingIdempotencyOwnershipTest {

    @Test
    void idempotencyKeyBelongingToAnotherUser_isRejected_notReturned() {
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

        User ownerOfExistingBooking = new User();
        ownerOfExistingBooking.setId(999L); // a different user than the requester below

        Booking existingBooking = new Booking();
        existingBooking.setUser(ownerOfExistingBooking);
        existingBooking.setPassengers(java.util.List.of());

        when(bookingRepo.findByIdempotencyKey("shared-key")).thenReturn(Optional.of(existingBooking));

        var req = new com.railmind.booking.dto.BookingDtos.CreateBookingRequest(
                1L, 1L, 2L, "SL", java.util.List.of(), "shared-key");

        // requester is user id 42, NOT the owner (999) of the booking that already used this key
        assertThatThrownBy(() -> bookingService.createBooking(42L, req))
                .isInstanceOf(ConflictException.class);
    }
}
