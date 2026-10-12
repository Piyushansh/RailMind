package com.railmind.disruption;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TripAlertRepository extends JpaRepository<TripAlert, Long> {
    List<TripAlert> findByBookingIdOrderByCreatedAtDesc(Long bookingId);
    Optional<TripAlert> findByIdAndBooking_Id(Long id, Long bookingId);
}
