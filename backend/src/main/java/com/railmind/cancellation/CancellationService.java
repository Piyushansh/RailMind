package com.railmind.cancellation;

import com.railmind.audit.AuditService;
import com.railmind.auth.User;
import com.railmind.auth.UserRepository;
import com.railmind.booking.BookingEntities.Booking;
import com.railmind.booking.BookingRepositories;
import com.railmind.booking.BookingService;
import com.railmind.cancellation.CancellationEntities.*;
import com.railmind.cancellation.CancellationRepositories.*;
import com.railmind.cancellation.RefundPolicyService.RefundQuote;
import com.railmind.cancellation.dto.CancellationDtos.*;
import com.railmind.common.exceptions.AppExceptions.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Orchestrates cancellations: computes the refund via RefundPolicyService
 * (the deterministic policy engine - always, no exceptions), frees the
 * seats, and records both a Cancellation and a Refund row. Two entry points
 * share the same finalize step:
 *
 * - cancel(): the normal user-initiated path, time-based refund policy.
 * - cancelDueToDisruption(): used only by RebookingService when RailMind's
 *   own disruption (delay/cancellation) forces a rebooking - always a full
 *   refund via RefundPolicyService.fullRefund(), never the user's choice.
 */
@Service
public class CancellationService {

    private final CancellationRepo cancellationRepo;
    private final RefundRepo refundRepo;
    private final BookingRepositories.PnrRepo pnrRepo;
    private final BookingService bookingService;
    private final RefundPolicyService refundPolicyService;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final com.railmind.notification.NotificationService notificationService;

    public CancellationService(CancellationRepo cancellationRepo, RefundRepo refundRepo,
                                BookingRepositories.PnrRepo pnrRepo,
                                BookingService bookingService, RefundPolicyService refundPolicyService,
                                UserRepository userRepository, AuditService auditService,
                                com.railmind.notification.NotificationService notificationService) {
        this.cancellationRepo = cancellationRepo;
        this.refundRepo = refundRepo;
        this.pnrRepo = pnrRepo;
        this.bookingService = bookingService;
        this.refundPolicyService = refundPolicyService;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    @Transactional
    public RefundDto cancel(Long userId, CancelRequest req) {
        Booking bookingBefore = bookingService.getRawEntity(req.bookingId(), userId);

        // Delay-adjusted actual departure: a delayed train's real departure is later than its
        // originally scheduled time, and a user cancelling a delayed-but-not-yet-departed train
        // must not be told their journey is "already completed".
        LocalDateTime departure = LocalDateTime.of(
                        bookingBefore.getSchedule().getDepartureDate(), bookingBefore.getSchedule().getDepartureTime())
                .plusMinutes(bookingBefore.getSchedule().getDelayMinutes() != null
                        ? bookingBefore.getSchedule().getDelayMinutes() : 0);
        if (!departure.isAfter(LocalDateTime.now())) {
            // Business rule (spec section 70): "Cannot cancel completed journey". This check is
            // deliberately only on the user-initiated path - RebookingService's disruption-driven
            // cancelDueToDisruption() must never be blocked by this, since it exists precisely to
            // move a passenger off a journey that RailMind itself is disrupting.
            throw new com.railmind.common.exceptions.AppExceptions.ConflictException(
                    "This journey has already been completed and cannot be cancelled.");
        }
        double hoursBefore = Duration.between(LocalDateTime.now(), departure).toMinutes() / 60.0;

        RefundQuote quote = refundPolicyService.calculate(bookingBefore.getTotalFare(), hoursBefore);

        return finalizeCancellation(userId, req.bookingId(), bookingBefore, quote,
                req.reason(), Math.max(hoursBefore, 0), false);
    }

    /** Used only by RebookingService - see class Javadoc. Not exposed via any controller. */
    @Transactional
    public RefundDto cancelDueToDisruption(Long userId, Long bookingId, String disruptionReason) {
        Booking bookingBefore = bookingService.getRawEntity(bookingId, userId);
        RefundQuote quote = refundPolicyService.fullRefund(bookingBefore.getTotalFare(), disruptionReason);
        return finalizeCancellation(userId, bookingId, bookingBefore, quote, disruptionReason, 0, false);
    }

    /** Admin-initiated cancellation (see AdminBookingController): not ownership-scoped - the
     * admin is acting on a customer's behalf. Same time-based refund policy and same
     * "cannot cancel a completed journey" rule as the user-initiated path; cancelledBy is
     * recorded as the admin, but notifications still go to the actual passenger. */
    @Transactional
    public RefundDto adminCancel(Long adminUserId, Long bookingId, String reason) {
        Booking bookingBefore = bookingService.getRawEntityById(bookingId);

        LocalDateTime departure = LocalDateTime.of(
                        bookingBefore.getSchedule().getDepartureDate(), bookingBefore.getSchedule().getDepartureTime())
                .plusMinutes(bookingBefore.getSchedule().getDelayMinutes() != null
                        ? bookingBefore.getSchedule().getDelayMinutes() : 0);
        if (!departure.isAfter(LocalDateTime.now())) {
            throw new com.railmind.common.exceptions.AppExceptions.ConflictException(
                    "This journey has already been completed and cannot be cancelled.");
        }
        double hoursBefore = Duration.between(LocalDateTime.now(), departure).toMinutes() / 60.0;
        RefundQuote quote = refundPolicyService.calculate(bookingBefore.getTotalFare(), hoursBefore);
        return finalizeCancellation(adminUserId, bookingId, bookingBefore, quote, reason, Math.max(hoursBefore, 0), true);
    }

    private RefundDto finalizeCancellation(Long actorUserId, Long bookingId, Booking bookingBefore,
                                            RefundQuote quote, String reason, double hoursBeforeDeparture,
                                            boolean adminInitiated) {
        User actor = userRepository.findById(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        User beneficiary = bookingBefore.getUser();

        Cancellation cancellation = new Cancellation();
        cancellation.setBooking(bookingBefore);
        cancellation.setCancelledBy(actor);
        cancellation.setReason(reason);
        cancellation.setHoursBeforeDeparture(BigDecimal.valueOf(hoursBeforeDeparture));
        cancellation = cancellationRepo.save(cancellation);

        Refund refund = new Refund();
        refund.setCancellation(cancellation);
        refund.setOriginalFare(quote.originalFare());
        refund.setDeductionPercent(quote.deductionPercent());
        refund.setRefundAmount(quote.refundAmount());
        refund.setStatus("PROCESSED");
        refund = refundRepo.save(refund);

        Booking cancelledBooking = adminInitiated
                ? bookingService.adminCancelConfirmedBooking(bookingId)
                : bookingService.cancelConfirmedBooking(bookingId, actorUserId);
        String pnrNumber = pnrRepo.findByBookingId(cancelledBooking.getId()).map(p -> p.getPnrNumber()).orElse(null);

        auditService.log(actorUserId, adminInitiated ? "ADMIN_CANCELLED_BOOKING" : "BOOKING_CANCELLED",
                "Booking", cancelledBooking.getId(), "refund=" + quote.refundAmount() + " reason=" + reason);

        notificationService.notify(beneficiary, "BOOKING_CANCELLED",
                "Booking cancelled",
                "Booking #" + cancelledBooking.getId() + (pnrNumber != null ? " (PNR " + pnrNumber + ")" : "")
                        + " has been cancelled.",
                cancelledBooking);
        notificationService.notify(beneficiary, "REFUND_PROCESSED",
                "Refund processed",
                "A refund of " + quote.refundAmount() + " has been processed for booking #" + cancelledBooking.getId()
                        + ". " + quote.explanation(),
                cancelledBooking);

        return new RefundDto(cancelledBooking.getId(), pnrNumber, quote.originalFare(), quote.deductionPercent(),
                quote.refundAmount(), quote.explanation(), refund.getStatus(), refund.getCreatedAt());
    }
}
