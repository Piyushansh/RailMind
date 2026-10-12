package com.railmind.support;

import com.railmind.ai.AiRepositories.ToolExecutionRepo;
import com.railmind.auth.User;
import com.railmind.auth.UserRepository;
import com.railmind.booking.BookingEntities.Booking;
import com.railmind.booking.BookingRepositories.BookingRepo;
import com.railmind.booking.BookingRepositories.PnrRepo;
import com.railmind.booking.BookingService;
import com.railmind.booking.dto.BookingDtos.BookingDto;
import com.railmind.cancellation.CancellationEntities.Cancellation;
import com.railmind.cancellation.CancellationRepositories.CancellationRepo;
import com.railmind.cancellation.CancellationRepositories.RefundRepo;
import com.railmind.common.exceptions.AppExceptions.ResourceNotFoundException;
import com.railmind.disruption.TripAlertRepository;
import com.railmind.payment.Payment;
import com.railmind.payment.PaymentRepository;
import com.railmind.support.dto.SupportDtos.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Houses the read-only composition logic SupportController exposes. Nothing
 * here mutates data - see CancellationService/BookingService for the
 * write paths, which support intentionally has no access to (only
 * SUPPORT_AGENT-or-ADMIN read endpoints are gated to this package;
 * mutating endpoints stay ADMIN-only elsewhere).
 */
@Service
public class SupportService {

    private final BookingService bookingService;
    private final PnrRepo pnrRepo;
    private final PaymentRepository paymentRepository;
    private final CancellationRepo cancellationRepo;
    private final RefundRepo refundRepo;
    private final TripAlertRepository tripAlertRepository;
    private final UserRepository userRepository;
    private final ToolExecutionRepo toolExecutionRepo;

    public SupportService(BookingService bookingService, PnrRepo pnrRepo, PaymentRepository paymentRepository,
                           CancellationRepo cancellationRepo, RefundRepo refundRepo,
                           TripAlertRepository tripAlertRepository, UserRepository userRepository,
                           ToolExecutionRepo toolExecutionRepo) {
        this.bookingService = bookingService;
        this.pnrRepo = pnrRepo;
        this.paymentRepository = paymentRepository;
        this.cancellationRepo = cancellationRepo;
        this.refundRepo = refundRepo;
        this.tripAlertRepository = tripAlertRepository;
        this.userRepository = userRepository;
        this.toolExecutionRepo = toolExecutionRepo;
    }

    public SupportBookingDetailDto viewBookingDetail(Long bookingId) {
        Booking rawBooking = bookingService.getRawEntityById(bookingId);
        BookingDto bookingDto = bookingService.getBookingForSupportOrAdmin(bookingId);

        PaymentSummary paymentSummary = paymentRepository.findByBookingId(bookingId)
                .map(p -> new PaymentSummary(p.getStatus(), p.getAmount(), p.getProvider(), p.getCreatedAt()))
                .orElse(null);

        CancellationSummary cancellationSummary = cancellationRepo.findByBookingId(bookingId)
                .map(this::toCancellationSummary)
                .orElse(null);

        List<DisruptionSummary> disruptions = tripAlertRepository.findByBookingIdOrderByCreatedAtDesc(bookingId)
                .stream()
                .map(a -> new DisruptionSummary(a.getAlertType(), a.getMessage(), a.isResolved(), a.getCreatedAt()))
                .toList();

        List<TimelineEvent> timeline = buildTimeline(bookingDto, paymentRepository.findByBookingId(bookingId).orElse(null),
                cancellationRepo.findByBookingId(bookingId).orElse(null), disruptions);

        return new SupportBookingDetailDto(bookingDto, rawBooking.getUser().getEmail(),
                paymentSummary, cancellationSummary, disruptions, timeline);
    }

    public SupportBookingDetailDto viewByPnr(String pnrNumber) {
        var pnr = pnrRepo.findByPnrNumber(pnrNumber)
                .orElseThrow(() -> new ResourceNotFoundException("No booking found for PNR " + pnrNumber));
        return viewBookingDetail(pnr.getBooking().getId());
    }

    /**
     * Restricted to USER-role accounts only - support must never be able to search up other
     * staff (SUPPORT_AGENT/ADMIN) accounts through the customer-lookup tool. That's a distinct,
     * narrower visibility rule than admin's own user search (AdminUserController), which
     * legitimately does need to see everyone.
     */
    public List<CustomerSearchResultDto> searchCustomers(String query) {
        if (query == null || query.isBlank()) return List.of();
        Specification<User> spec = Specification.<User>where((root, q, cb) -> cb.like(root.get("roles"), "%USER%"))
                .and((root, q, cb) -> cb.or(
                        cb.like(cb.lower(root.get("fullName")), "%" + query.toLowerCase() + "%"),
                        cb.like(cb.lower(root.get("email")), "%" + query.toLowerCase() + "%"),
                        cb.like(root.get("phone"), "%" + query + "%")));
        return userRepository.findAll(spec, PageRequest.of(0, 25)).stream()
                .map(u -> new CustomerSearchResultDto(u.getId(), u.getFullName(), u.getEmail(), u.isEnabled()))
                .toList();
    }

    public List<AiActionDto> viewAiActionsForUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found");
        }
        return toolExecutionRepo.findByConversation_User_IdOrderByCreatedAtDesc(userId).stream()
                .map(e -> new AiActionDto(e.getConversation().getId(), e.getToolName(), e.isSuccess(), e.getCreatedAt()))
                .toList();
    }

    public List<BookingDto> viewBookingsForUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found");
        }
        return bookingService.getUserBookings(userId);
    }

    private CancellationSummary toCancellationSummary(Cancellation c) {
        var refund = refundRepo.findByCancellationId(c.getId());
        return new CancellationSummary(c.getReason(), c.getHoursBeforeDeparture(), c.getCreatedAt(),
                refund.map(com.railmind.cancellation.CancellationEntities.Refund::getRefundAmount).orElse(null),
                refund.map(com.railmind.cancellation.CancellationEntities.Refund::getDeductionPercent).orElse(null),
                refund.map(com.railmind.cancellation.CancellationEntities.Refund::getStatus).orElse(null));
    }

    private List<TimelineEvent> buildTimeline(BookingDto booking, Payment payment, Cancellation cancellation,
                                               List<DisruptionSummary> disruptions) {
        List<TimelineEvent> events = new ArrayList<>();
        events.add(new TimelineEvent("Booking created", booking.createdAt()));
        if (payment != null) {
            events.add(new TimelineEvent("Payment " + payment.getStatus().toLowerCase(), payment.getCreatedAt()));
        }
        for (DisruptionSummary d : disruptions) {
            events.add(new TimelineEvent("Disruption: " + d.alertType(), d.createdAt()));
        }
        if (cancellation != null) {
            events.add(new TimelineEvent("Booking cancelled", cancellation.getCreatedAt()));
            refundRepo.findByCancellationId(cancellation.getId())
                    .ifPresent(r -> events.add(new TimelineEvent("Refund " + r.getStatus().toLowerCase(), r.getCreatedAt())));
        }
        return events.stream().sorted(Comparator.comparing(TimelineEvent::occurredAt)).toList();
    }
}
