package com.railmind.payment;

import com.railmind.audit.AuditService;
import com.railmind.booking.BookingEntities.Booking;
import com.railmind.booking.BookingRepositories;
import com.railmind.booking.BookingService;
import com.railmind.booking.dto.BookingDtos.BookingDto;
import com.railmind.common.exceptions.AppExceptions.*;
import com.railmind.payment.PaymentProvider.ChargeResult;
import com.railmind.payment.dto.PaymentDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final BookingRepositories.BookingRepo bookingRepo;
    private final BookingService bookingService;
    private final PaymentProvider paymentProvider;
    private final AuditService auditService;
    private final com.railmind.notification.NotificationService notificationService;

    public PaymentService(PaymentRepository paymentRepository, BookingRepositories.BookingRepo bookingRepo,
                           BookingService bookingService, PaymentProvider paymentProvider, AuditService auditService,
                           com.railmind.notification.NotificationService notificationService) {
        this.paymentRepository = paymentRepository;
        this.bookingRepo = bookingRepo;
        this.bookingService = bookingService;
        this.paymentProvider = paymentProvider;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    @Transactional
    public PaymentResultDto pay(Long userId, PayRequest req) {
        var existing = paymentRepository.findByIdempotencyKey(req.idempotencyKey());
        if (existing.isPresent()) {
            Payment existingPayment = existing.get();
            if (!existingPayment.getBooking().getUser().getId().equals(userId)) {
                throw new ConflictException("This idempotency key was already used by a different request.");
            }
            return toDto(existingPayment);
        }

        Booking booking = bookingService.getEntityOwnedBy(req.bookingId(), userId);
        if (!"PENDING_PAYMENT".equals(booking.getStatus())) {
            throw new ConflictException("Booking is not awaiting payment (status: " + booking.getStatus() + ").");
        }

        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(booking.getTotalFare());
        payment.setIdempotencyKey(req.idempotencyKey());

        ChargeResult result = paymentProvider.charge(booking.getId(), booking.getTotalFare(), req.idempotencyKey());

        if (result.success()) {
            payment.setStatus("SUCCESS");
            payment.setProviderReference(result.providerReference());
            payment = paymentRepository.save(payment);

            BookingDto confirmed = bookingService.confirmAfterPayment(booking.getId());
            auditService.log(userId, "PAYMENT_SUCCESS", "Payment", payment.getId(), "booking=" + booking.getId());

            notificationService.notify(booking.getUser(), "BOOKING_CONFIRMED",
                    "Booking confirmed",
                    "Your booking on " + confirmed.trainNumber() + " (" + confirmed.trainName() + ") from "
                            + confirmed.sourceStationCode() + " to " + confirmed.destinationStationCode()
                            + " is confirmed. PNR: " + confirmed.pnrNumber() + ".",
                    booking);

            return new PaymentResultDto(payment.getId(), booking.getId(), "SUCCESS",
                    confirmed.status(), confirmed.pnrNumber(), null);
        } else {
            payment.setStatus("FAILED");
            payment = paymentRepository.save(payment);
            bookingService.releaseHeldSeats(booking.getId());
            auditService.log(userId, "PAYMENT_FAILED", "Payment", payment.getId(), result.failureReason());

            notificationService.notify(booking.getUser(), "PAYMENT_FAILED",
                    "Payment failed",
                    "Your payment for booking #" + booking.getId() + " could not be completed"
                            + (result.failureReason() != null ? " (" + result.failureReason() + ")" : "")
                            + ". The held seats have been released.",
                    booking);

            return new PaymentResultDto(payment.getId(), booking.getId(), "FAILED",
                    "EXPIRED", null, result.failureReason());
        }
    }

    private PaymentResultDto toDto(Payment payment) {
        return new PaymentResultDto(payment.getId(), payment.getBooking().getId(), payment.getStatus(),
                payment.getBooking().getStatus(), null, null);
    }
}
