package com.railmind.booking;

import com.railmind.booking.dto.PnrDetailDtos.*;
import com.railmind.cancellation.CancellationRepositories.CancellationRepo;
import com.railmind.cancellation.CancellationRepositories.RefundRepo;
import com.railmind.disruption.TripAlertRepository;
import com.railmind.payment.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Composes the full user-facing PNR/booking detail view: BookingService's
 * own ownership check (getBookingByPnr / getBooking) plus payment,
 * cancellation/refund, and disruption status pulled from their respective
 * repositories. Every method here is ownership-scoped to the calling user -
 * there is no way to reach another user's data through this class, unlike
 * SupportService's equivalent (which is intentionally NOT ownership-scoped,
 * gated by role instead - see that class's own Javadoc).
 */
@Service
public class PnrDetailService {

    private final BookingService bookingService;
    private final PaymentRepository paymentRepository;
    private final CancellationRepo cancellationRepo;
    private final RefundRepo refundRepo;
    private final TripAlertRepository tripAlertRepository;

    public PnrDetailService(BookingService bookingService, PaymentRepository paymentRepository,
                             CancellationRepo cancellationRepo, RefundRepo refundRepo,
                             TripAlertRepository tripAlertRepository) {
        this.bookingService = bookingService;
        this.paymentRepository = paymentRepository;
        this.cancellationRepo = cancellationRepo;
        this.refundRepo = refundRepo;
        this.tripAlertRepository = tripAlertRepository;
    }

    @Transactional(readOnly = true)
    public PnrDetailDto getDetailByPnr(String pnrNumber, Long userId) {
        var booking = bookingService.getBookingByPnr(pnrNumber, userId); // ownership check happens here
        return compose(booking);
    }

    @Transactional(readOnly = true)
    public PnrDetailDto getDetailByBookingId(Long bookingId, Long userId) {
        var booking = bookingService.getBooking(bookingId, userId); // ownership check happens here
        return compose(booking);
    }

    private PnrDetailDto compose(com.railmind.booking.dto.BookingDtos.BookingDto booking) {
        PaymentStatus paymentStatus = paymentRepository.findByBookingId(booking.bookingId())
                .map(p -> new PaymentStatus(p.getStatus(), p.getAmount(), p.getCreatedAt()))
                .orElse(null);

        CancellationStatus cancellationStatus = cancellationRepo.findByBookingId(booking.bookingId())
                .map(c -> new CancellationStatus(c.getReason(), c.getCreatedAt()))
                .orElse(null);

        RefundStatus refundStatus = cancellationRepo.findByBookingId(booking.bookingId())
                .flatMap(c -> refundRepo.findByCancellationId(c.getId()))
                .map(r -> new RefundStatus(r.getOriginalFare(), r.getDeductionPercent(), r.getRefundAmount(),
                        r.getStatus(), r.getCreatedAt()))
                .orElse(null);

        var disruptions = tripAlertRepository.findByBookingIdOrderByCreatedAtDesc(booking.bookingId()).stream()
                .map(a -> new DisruptionStatus(a.getAlertType(), a.getMessage(), a.isResolved(), a.getCreatedAt()))
                .toList();

        return new PnrDetailDto(booking, paymentStatus, cancellationStatus, refundStatus, disruptions);
    }
}
