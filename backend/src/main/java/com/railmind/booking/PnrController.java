package com.railmind.booking;

import com.railmind.audit.AuditService;
import com.railmind.auth.CurrentUser;
import com.railmind.booking.dto.PnrDetailDtos.PnrDetailDto;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Plain-user PNR lookup, per spec section 61 ("GET /api/pnr/{pnr}"). This is
 * distinct from SupportController's PNR lookup: that one is role-gated and
 * can see ANY user's PNR (for assisting customers); this one is available to
 * any authenticated user but strictly scoped to their own bookings only -
 * see PnrDetailService/BookingService.getBookingByPnr for the ownership check.
 */
@RestController
@RequestMapping("/api/v1/pnr")
public class PnrController {

    private final PnrDetailService pnrDetailService;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    public PnrController(PnrDetailService pnrDetailService, CurrentUser currentUser, AuditService auditService) {
        this.pnrDetailService = pnrDetailService;
        this.currentUser = currentUser;
        this.auditService = auditService;
    }

    @GetMapping("/{pnrNumber}")
    public PnrDetailDto lookup(@PathVariable String pnrNumber, Authentication auth) {
        Long userId = currentUser.resolveId(auth);
        PnrDetailDto dto = pnrDetailService.getDetailByPnr(pnrNumber, userId);
        auditService.log(userId, "USER_VIEWED_OWN_PNR", "Booking", dto.booking().bookingId(), "pnr=" + pnrNumber);
        return dto;
    }
}
