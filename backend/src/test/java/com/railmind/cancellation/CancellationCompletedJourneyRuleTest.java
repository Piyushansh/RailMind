package com.railmind.cancellation;

import com.railmind.audit.AuditService;
import com.railmind.auth.UserRepository;
import com.railmind.booking.BookingEntities.Booking;
import com.railmind.booking.BookingRepositories;
import com.railmind.booking.BookingService;
import com.railmind.cancellation.dto.CancellationDtos.CancelRequest;
import com.railmind.common.exceptions.AppExceptions.ConflictException;
import com.railmind.notification.NotificationService;
import com.railmind.train.ScheduleEntities.TrainSchedule;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression test for spec section 70's "Cannot cancel completed journey" rule,
 * added this pass: CancellationService.cancel must reject a booking whose
 * (delay-adjusted) departure has already elapsed, even though the booking's
 * status is still CONFIRMED (nothing currently transitions it to a
 * "completed" status automatically). Deliberately does NOT test
 * cancelDueToDisruption, which must remain unaffected by this check - see
 * that method's Javadoc.
 */
class CancellationCompletedJourneyRuleTest {

    @Test
    void cannotCancel_aBookingWhoseJourneyAlreadyHappened() {
        CancellationRepositories.CancellationRepo cancellationRepo = mock(CancellationRepositories.CancellationRepo.class);
        CancellationRepositories.RefundRepo refundRepo = mock(CancellationRepositories.RefundRepo.class);
        BookingRepositories.PnrRepo pnrRepo = mock(BookingRepositories.PnrRepo.class);
        BookingService bookingService = mock(BookingService.class);
        RefundPolicyService refundPolicyService = mock(RefundPolicyService.class);
        UserRepository userRepository = mock(UserRepository.class);
        AuditService auditService = mock(AuditService.class);
        NotificationService notificationService = mock(NotificationService.class);

        CancellationService cancellationService = new CancellationService(cancellationRepo, refundRepo, pnrRepo,
                bookingService, refundPolicyService, userRepository, auditService, notificationService);

        TrainSchedule pastSchedule = new TrainSchedule();
        pastSchedule.setDepartureDate(LocalDate.now().minusDays(2));
        pastSchedule.setDepartureTime(LocalTime.of(9, 0));
        pastSchedule.setDelayMinutes(0);

        Booking booking = new Booking();
        booking.setSchedule(pastSchedule);
        booking.setTotalFare(java.math.BigDecimal.valueOf(1000));

        when(bookingService.getRawEntity(5L, 1L)).thenReturn(booking);

        var req = new CancelRequest(5L, "changed my mind");

        assertThatThrownBy(() -> cancellationService.cancel(1L, req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already been completed");
    }

    @Test
    void delayedButNotYetDeparted_journey_canStillBeCancelled() {
        CancellationRepositories.CancellationRepo cancellationRepo = mock(CancellationRepositories.CancellationRepo.class);
        CancellationRepositories.RefundRepo refundRepo = mock(CancellationRepositories.RefundRepo.class);
        BookingRepositories.PnrRepo pnrRepo = mock(BookingRepositories.PnrRepo.class);
        BookingService bookingService = mock(BookingService.class);
        RefundPolicyService refundPolicyService = mock(RefundPolicyService.class);
        UserRepository userRepository = mock(UserRepository.class);
        AuditService auditService = mock(AuditService.class);
        NotificationService notificationService = mock(NotificationService.class);

        CancellationService cancellationService = new CancellationService(cancellationRepo, refundRepo, pnrRepo,
                bookingService, refundPolicyService, userRepository, auditService, notificationService);

        // Originally scheduled departure was an hour ago, but a 180-minute delay means the
        // train has NOT actually left yet - this must NOT be treated as "completed".
        TrainSchedule delayedSchedule = new TrainSchedule();
        delayedSchedule.setDepartureDate(LocalDate.now());
        delayedSchedule.setDepartureTime(LocalTime.now().minusHours(1));
        delayedSchedule.setDelayMinutes(180);

        Booking booking = new Booking();
        booking.setSchedule(delayedSchedule);
        booking.setTotalFare(java.math.BigDecimal.valueOf(1000));

        com.railmind.auth.User user = new com.railmind.auth.User();
        user.setId(1L);

        when(bookingService.getRawEntity(5L, 1L)).thenReturn(booking);
        when(userRepository.findById(1L)).thenReturn(java.util.Optional.of(user));
        when(refundPolicyService.calculate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyDouble()))
                .thenReturn(new RefundPolicyService.RefundQuote(
                        java.math.BigDecimal.valueOf(1000), java.math.BigDecimal.valueOf(25),
                        java.math.BigDecimal.valueOf(750), "25% deduction"));
        when(cancellationRepo.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(refundRepo.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(bookingService.cancelConfirmedBooking(5L, 1L)).thenReturn(booking);

        // Should NOT throw - this is exactly the case the delay-adjustment exists for.
        cancellationService.cancel(1L, req(bookingService));
    }

    private CancelRequest req(BookingService ignored) {
        return new CancelRequest(5L, "delayed, no longer needed");
    }
}
