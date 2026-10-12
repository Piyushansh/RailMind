package com.railmind.admin;

import com.railmind.admin.dto.AdminDtos.PageResponse;
import com.railmind.cancellation.CancellationRepositories.RefundRepo;
import com.railmind.cancellation.CancellationEntities.Refund;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Read-only admin view of processed refunds (spec: admin platform needs
 * "refunds"). Refunds are only ever created as part of a cancellation (see
 * CancellationService) - there is no separate "issue a refund" write path,
 * so this controller is intentionally list/detail only.
 */
@RestController
@RequestMapping("/api/v1/admin/refunds")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRefundController {

    private final RefundRepo refundRepo;

    public AdminRefundController(RefundRepo refundRepo) {
        this.refundRepo = refundRepo;
    }

    public record RefundAdminDto(Long id, Long bookingId, String userEmail, BigDecimal originalFare,
                                  BigDecimal deductionPercent, BigDecimal refundAmount, String status, Instant createdAt) {
        static RefundAdminDto from(Refund r) {
            var cancellation = r.getCancellation();
            return new RefundAdminDto(r.getId(), cancellation.getBooking().getId(),
                    cancellation.getBooking().getUser().getEmail(), r.getOriginalFare(),
                    r.getDeductionPercent(), r.getRefundAmount(), r.getStatus(), r.getCreatedAt());
        }
    }

    @GetMapping
    public PageResponse<RefundAdminDto> list(@RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        var result = refundRepo.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(RefundAdminDto::from);
        return PageResponse.from(result);
    }
}
