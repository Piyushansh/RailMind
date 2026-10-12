package com.railmind.disruption;

import com.railmind.audit.AuditService;
import com.railmind.booking.BookingEntities.Booking;
import com.railmind.booking.BookingEntities.BookingPassenger;
import com.railmind.booking.BookingService;
import com.railmind.booking.dto.BookingDtos.BookingDto;
import com.railmind.booking.dto.BookingDtos.PassengerRequest;
import com.railmind.cancellation.CancellationService;
import com.railmind.common.exceptions.AppExceptions.BadRequestException;
import com.railmind.common.exceptions.AppExceptions.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Orchestrates the human-approved rebooking step of the disruption workflow.
 * Depends on BookingService and CancellationService rather than the other
 * way around, specifically to avoid a circular bean dependency (both of
 * those already depend on each other in one direction) while still reusing
 * their logic instead of duplicating seat-holding or refund calculation here.
 *
 * Safety property this is built around: seats on the NEW schedule are secured
 * FIRST; the OLD booking is only cancelled (with a full disruption refund)
 * once that succeeds. If the alternative has no seats left by the time the
 * user approves, the original booking is completely untouched and the user
 * gets a clear error rather than being left with neither booking.
 */
@Service
public class RebookingService {

    private final BookingService bookingService;
    private final CancellationService cancellationService;
    private final TripAlertRepository tripAlertRepository;
    private final AuditService auditService;

    public RebookingService(BookingService bookingService, CancellationService cancellationService,
                             TripAlertRepository tripAlertRepository, AuditService auditService) {
        this.bookingService = bookingService;
        this.cancellationService = cancellationService;
        this.tripAlertRepository = tripAlertRepository;
        this.auditService = auditService;
    }

    @Transactional
    public BookingDto approveRebooking(Long userId, Long bookingId, Long alertId) {
        TripAlert alert = tripAlertRepository.findByIdAndBooking_Id(alertId, bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip alert not found for this booking"));
        if (alert.getSuggestedAlternativeSchedule() == null) {
            throw new BadRequestException("This alert has no suggested alternative to rebook onto.");
        }

        Booking oldBooking = bookingService.getRawEntity(bookingId, userId); // already ownership-scoped
        if (!"CONFIRMED".equals(oldBooking.getStatus())) {
            throw new BadRequestException("Only a confirmed booking can be rebooked (status: " + oldBooking.getStatus() + ").");
        }

        List<PassengerRequest> passengerRequests = oldBooking.getPassengers().stream()
                .map(this::toPassengerRequest)
                .toList();

        // Step 1: secure seats on the alternative FIRST. If this throws (no seats left),
        // the old booking below is never touched - nothing has been committed yet.
        BookingDto newBooking = bookingService.createConfirmedRebooking(
                userId,
                alert.getSuggestedAlternativeSchedule().getId(),
                oldBooking.getSourceStation().getId(),
                oldBooking.getDestinationStation().getId(),
                oldBooking.getTravelClass(),
                passengerRequests,
                oldBooking.getId());

        // Step 2: only now cancel the old booking, with a full (disruption-caused) refund.
        cancellationService.cancelDueToDisruption(userId, oldBooking.getId(),
                "Rebooked to train " + newBooking.trainNumber() + " due to a service disruption");

        alert.setResolved(true);
        tripAlertRepository.save(alert);

        TripAlert completionAlert = new TripAlert();
        completionAlert.setBooking(oldBooking); // recorded against the original booking's timeline
        completionAlert.setAlertType("REBOOKING_COMPLETED");
        completionAlert.setMessage("Rebooked to train " + newBooking.trainNumber() + " (" + newBooking.trainName()
                + "), PNR " + newBooking.pnrNumber() + ".");
        tripAlertRepository.save(completionAlert);

        auditService.log(userId, "REBOOKING_APPROVED", "Booking", bookingId,
                "newBooking=" + newBooking.bookingId() + " alert=" + alertId);

        return newBooking;
    }

    /**
     * User explicitly declines a suggested rebooking - the alert is marked resolved (so it
     * stops showing as an open recommendation) but nothing about the original booking changes.
     * Rebooking must never happen without explicit approval (see class Javadoc's safety
     * property); this is the other half of that guarantee - an alert the user actively dismissed
     * must never be silently auto-approved later either.
     */
    @Transactional
    public void rejectRebooking(Long userId, Long bookingId, Long alertId) {
        // Ownership check first, same as approveRebooking - a user must own the booking this
        // alert belongs to before they can act on it at all.
        bookingService.getRawEntity(bookingId, userId);

        TripAlert alert = tripAlertRepository.findByIdAndBooking_Id(alertId, bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip alert not found for this booking"));
        if (alert.isResolved()) {
            throw new BadRequestException("This alert has already been resolved.");
        }
        alert.setResolved(true);
        tripAlertRepository.save(alert);

        auditService.log(userId, "REBOOKING_REJECTED", "Booking", bookingId, "alert=" + alertId);
    }

    private PassengerRequest toPassengerRequest(BookingPassenger bp) {
        return new PassengerRequest(
                bp.getPassenger().getFullName(),
                bp.getPassenger().getAge(),
                bp.getPassenger().getGender(),
                bp.getBerthPreference());
    }
}
