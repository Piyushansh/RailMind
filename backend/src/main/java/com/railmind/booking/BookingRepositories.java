package com.railmind.booking;

import com.railmind.booking.BookingEntities.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.List;
import java.util.Optional;

public interface BookingRepositories {

    interface PassengerRepo extends JpaRepository<Passenger, Long> {
        List<Passenger> findByOwnerUserIdAndSavedTrueOrderByFullNameAsc(Long ownerUserId);
        Optional<Passenger> findByIdAndOwnerUserId(Long id, Long ownerUserId);
    }

    interface BookingRepo extends JpaRepository<Booking, Long>, JpaSpecificationExecutor<Booking> {
        List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);
        Optional<Booking> findByIdAndUserId(Long id, Long userId);
        Optional<Booking> findByIdempotencyKey(String idempotencyKey);
        List<Booking> findByStatusAndHoldExpiresAtBefore(String status, java.time.Instant cutoff);
        List<Booking> findByScheduleIdAndStatus(Long scheduleId, String status);
    }

    interface BookingPassengerRepo extends JpaRepository<BookingPassenger, Long> {
        List<BookingPassenger> findByBookingId(Long bookingId);
    }

    interface PnrRepo extends JpaRepository<Pnr, Long> {
        Optional<Pnr> findByPnrNumber(String pnrNumber);
        Optional<Pnr> findByBookingId(Long bookingId);
    }
}
