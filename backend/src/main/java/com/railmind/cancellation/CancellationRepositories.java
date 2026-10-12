package com.railmind.cancellation;

import com.railmind.cancellation.CancellationEntities.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CancellationRepositories {
    interface CancellationRepo extends JpaRepository<Cancellation, Long> {
        Optional<Cancellation> findByBookingId(Long bookingId);
    }
    interface RefundRepo extends JpaRepository<Refund, Long> {
        Optional<Refund> findByCancellationId(Long cancellationId);
    }
}
