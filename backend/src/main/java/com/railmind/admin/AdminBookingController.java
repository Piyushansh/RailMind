package com.railmind.admin;

import com.railmind.admin.dto.AdminDtos.PageResponse;
import com.railmind.audit.AuditService;
import com.railmind.auth.CurrentUser;
import com.railmind.booking.BookingEntities.Booking;
import com.railmind.booking.BookingRepositories.BookingRepo;
import com.railmind.booking.BookingRepositories.PnrRepo;
import com.railmind.booking.BookingService;
import com.railmind.cancellation.CancellationService;
import com.railmind.cancellation.dto.CancellationDtos.RefundDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Admin-only booking search/inspection and admin-initiated cancel+refund.
 * Deliberately reuses BookingService/CancellationService rather than
 * re-implementing seat release or refund math - see
 * CancellationService.adminCancel and BookingService.adminCancelConfirmedBooking.
 */
@RestController
@RequestMapping("/api/v1/admin/bookings")
@PreAuthorize("hasRole('ADMIN')")
public class AdminBookingController {

    private final BookingRepo bookingRepo;
    private final PnrRepo pnrRepo;
    private final BookingService bookingService;
    private final CancellationService cancellationService;
    private final AuditService auditService;
    private final CurrentUser currentUser;

    public AdminBookingController(BookingRepo bookingRepo, PnrRepo pnrRepo, BookingService bookingService,
                                   CancellationService cancellationService, AuditService auditService,
                                   CurrentUser currentUser) {
        this.bookingRepo = bookingRepo;
        this.pnrRepo = pnrRepo;
        this.bookingService = bookingService;
        this.cancellationService = cancellationService;
        this.auditService = auditService;
        this.currentUser = currentUser;
    }

    public record BookingAdminDto(Long id, String userEmail, String pnrNumber, String trainNumber,
                                   String sourceStationCode, String destinationStationCode, String travelClass,
                                   String status, BigDecimal totalFare, boolean tripGuardianEnabled, Instant createdAt) {}

    public record AdminCancelRequest(@NotBlank String reason) {}

    private BookingAdminDto toAdminDto(Booking b) {
        String pnr = pnrRepo.findByBookingId(b.getId()).map(p -> p.getPnrNumber()).orElse(null);
        return new BookingAdminDto(b.getId(), b.getUser().getEmail(), pnr, b.getSchedule().getTrain().getNumber(),
                b.getSourceStation().getCode(), b.getDestinationStation().getCode(), b.getTravelClass(),
                b.getStatus(), b.getTotalFare(), b.isTripGuardianEnabled(), b.getCreatedAt());
    }

    @GetMapping
    public PageResponse<BookingAdminDto> list(@RequestParam(required = false) String status,
                                               @RequestParam(required = false) String userEmail,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size,
                                               @RequestParam(defaultValue = "createdAt") String sortBy,
                                               @RequestParam(defaultValue = "desc") String sortDir) {
        var sort = Sort.by(sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
        var spec = SpecUtils.<Booking>and(
                SpecUtils.equalsValue("status", status),
                userEmail != null && !userEmail.isBlank()
                        ? (Specification<Booking>) (root, q, cb) -> cb.like(cb.lower(root.get("user").get("email")), "%" + userEmail.toLowerCase() + "%")
                        : null);
        var result = bookingRepo.findAll(spec, PageRequest.of(page, size, sort)).map(this::toAdminDto);
        return PageResponse.from(result);
    }

    @GetMapping("/{id}")
    public BookingAdminDto detail(@PathVariable Long id) {
        return toAdminDto(bookingService.getRawEntityById(id));
    }

    /** Admin-initiated cancellation, producing a refund via the same policy engine as the
     * user-facing path (see CancellationService.adminCancel). Reason is required for the
     * audit trail. */
    @PostMapping("/{id}/cancel")
    public RefundDto cancel(@PathVariable Long id, @Valid @RequestBody AdminCancelRequest request, Authentication auth) {
        return cancellationService.adminCancel(currentUser.resolveId(auth), id, request.reason());
    }
}
