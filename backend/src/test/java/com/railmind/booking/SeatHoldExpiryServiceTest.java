package com.railmind.booking;

import com.railmind.audit.AuditService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.*;

/**
 * Proves the sweep's key resilience property: if releasing one expired
 * booking throws (e.g. it changed state concurrently), the sweep still
 * processes the rest instead of aborting the whole batch.
 */
class SeatHoldExpiryServiceTest {

    @Test
    void oneFailingBooking_doesNotStopTheRestOfTheSweep() {
        BookingService bookingService = mock(BookingService.class);
        AuditService auditService = mock(AuditService.class);

        when(bookingService.findExpiredHoldBookingIds()).thenReturn(List.of(1L, 2L, 3L));
        doThrow(new RuntimeException("simulated concurrent state change")).when(bookingService).releaseHeldSeats(2L);

        SeatHoldExpiryService sweeper = new SeatHoldExpiryService(bookingService, auditService);
        sweeper.sweepExpiredHolds();

        verify(bookingService).releaseHeldSeats(1L);
        verify(bookingService).releaseHeldSeats(2L);
        verify(bookingService).releaseHeldSeats(3L); // still attempted despite booking 2 failing
        verify(auditService, times(2)).log(isNull(), eq("SEAT_HOLD_EXPIRED"), eq("Booking"), any(), any());
    }
}
