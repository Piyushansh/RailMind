package com.railmind.booking;

import com.railmind.audit.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Scheduled sweep that releases seats held by abandoned PENDING_PAYMENT
 * bookings. Without this, a user who starts a booking and never pays would
 * permanently block a seat - this is what makes "abandoned payment must not
 * permanently block inventory" actually true rather than aspirational.
 *
 * Each booking is expired in its own transaction (via BookingService.releaseHeldSeats,
 * already used for payment-failure cleanup) so one bad row can't abort the
 * whole sweep, and every expiry is audit-logged the same way a user-initiated
 * cancellation would be.
 */
@Service
public class SeatHoldExpiryService {

    private static final Logger log = LoggerFactory.getLogger(SeatHoldExpiryService.class);

    private final BookingService bookingService;
    private final AuditService auditService;

    public SeatHoldExpiryService(BookingService bookingService, AuditService auditService) {
        this.bookingService = bookingService;
        this.auditService = auditService;
    }

    @Scheduled(fixedRateString = "${railmind.booking.hold-expiry-sweep-interval-ms}")
    public void sweepExpiredHolds() {
        List<Long> expired = bookingService.findExpiredHoldBookingIds();
        for (Long bookingId : expired) {
            try {
                bookingService.releaseHeldSeats(bookingId);
                auditService.log(null, "SEAT_HOLD_EXPIRED", "Booking", bookingId,
                        "Seat hold expired before payment; seats released and booking marked EXPIRED.");
            } catch (Exception ex) {
                // one stuck/already-changed booking must never stop the rest of the sweep
                log.warn("Failed to expire seat hold for booking {}: {}", bookingId, ex.getMessage());
            }
        }
        if (!expired.isEmpty()) {
            log.info("Seat hold sweep expired {} abandoned booking(s).", expired.size());
        }
    }
}
