package com.railmind.support;

import com.railmind.audit.AuditService;
import com.railmind.auth.CurrentUser;
import com.railmind.support.dto.SupportDtos.*;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * SUPPORT_AGENT (and ADMIN) can look up ANY user's booking/PNR/AI history to
 * assist a customer - this is the one place in the app that's intentionally
 * NOT scoped to "your own data", gated by role via @PreAuthorize instead.
 * Every lookup is audit-logged specifically because of that: a support agent
 * viewing a customer's booking needs a trail the same way an admin action
 * would. SUPPORT_AGENT gets read-only visibility here - no endpoint in this
 * controller can create, modify, or cancel anything (that stays
 * AdminBookingController-only). Customer search is deliberately restricted
 * to USER-role accounts (see SupportService.searchCustomers) so a support
 * agent can never enumerate other staff accounts through this tool.
 */
@RestController
@RequestMapping("/api/v1/support")
@PreAuthorize("hasAnyRole('SUPPORT_AGENT', 'ADMIN')")
@Validated
public class SupportController {

    private final SupportService supportService;
    private final AuditService auditService;
    private final CurrentUser currentUser;

    public SupportController(SupportService supportService, AuditService auditService, CurrentUser currentUser) {
        this.supportService = supportService;
        this.auditService = auditService;
        this.currentUser = currentUser;
    }

    @GetMapping("/bookings/{id}")
    public SupportBookingDetailDto viewBooking(@PathVariable Long id, Authentication auth) {
        Long agentId = currentUser.resolveId(auth);
        SupportBookingDetailDto dto = supportService.viewBookingDetail(id);
        auditService.log(agentId, "SUPPORT_VIEWED_BOOKING", "Booking", id, null);
        return dto;
    }

    @GetMapping("/pnr/{pnrNumber}")
    public SupportBookingDetailDto viewByPnr(@PathVariable String pnrNumber, Authentication auth) {
        Long agentId = currentUser.resolveId(auth);
        SupportBookingDetailDto dto = supportService.viewByPnr(pnrNumber);
        auditService.log(agentId, "SUPPORT_VIEWED_PNR", "Booking", dto.booking().bookingId(), "pnr=" + pnrNumber);
        return dto;
    }

    @GetMapping("/customers/search")
    public List<CustomerSearchResultDto> searchCustomers(@RequestParam @NotBlank String query, Authentication auth) {
        Long agentId = currentUser.resolveId(auth);
        List<CustomerSearchResultDto> results = supportService.searchCustomers(query);
        // Audited with the query itself (not a customer id, since a search can return several) -
        // this is what lets "who looked up which customers" be reconstructed later.
        auditService.log(agentId, "SUPPORT_SEARCHED_CUSTOMERS", "User", null, "query=" + query + " results=" + results.size());
        return results;
    }

    @GetMapping("/users/{userId}/ai-actions")
    public List<AiActionDto> viewAiActions(@PathVariable Long userId, Authentication auth) {
        Long agentId = currentUser.resolveId(auth);
        List<AiActionDto> actions = supportService.viewAiActionsForUser(userId);
        auditService.log(agentId, "SUPPORT_VIEWED_AI_ACTIONS", "User", userId, "count=" + actions.size());
        return actions;
    }

    @GetMapping("/users/{userId}/bookings")
    public List<com.railmind.booking.dto.BookingDtos.BookingDto> viewBookings(@PathVariable Long userId, Authentication auth) {
        Long agentId = currentUser.resolveId(auth);
        var bookings = supportService.viewBookingsForUser(userId);
        auditService.log(agentId, "SUPPORT_VIEWED_USER_BOOKINGS", "User", userId, "count=" + bookings.size());
        return bookings;
    }
}
