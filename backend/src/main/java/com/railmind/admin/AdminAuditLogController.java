package com.railmind.admin;

import com.railmind.admin.dto.AdminDtos.PageResponse;
import com.railmind.audit.AuditLog;
import com.railmind.audit.AuditLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * Read-only admin view of the audit trail (spec: admin platform needs
 * "audit-logs"). No write endpoints here deliberately - the audit log is
 * append-only and every write path already goes through AuditService from
 * the action that generated it.
 */
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditLogController {

    private final AuditLogRepository repository;

    public AdminAuditLogController(AuditLogRepository repository) {
        this.repository = repository;
    }

    public record AuditLogDto(Long id, Long actorUserId, String action, String entityType, Long entityId,
                               String details, String correlationId, Instant createdAt) {
        static AuditLogDto from(AuditLog a) {
            return new AuditLogDto(a.getId(), a.getActorUserId(), a.getAction(), a.getEntityType(), a.getEntityId(),
                    a.getDetails(), a.getCorrelationId(), a.getCreatedAt());
        }
    }

    @GetMapping
    public PageResponse<AuditLogDto> list(@RequestParam(required = false) String action,
                                           @RequestParam(required = false) Long actorUserId,
                                           @RequestParam(required = false) String entityType,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "50") int size) {
        var sort = Sort.by(Sort.Direction.DESC, "createdAt");
        var spec = SpecUtils.<AuditLog>and(
                action != null && !action.isBlank()
                        ? (Specification<AuditLog>) (root, q, cb) -> cb.like(cb.lower(root.get("action")), "%" + action.toLowerCase() + "%")
                        : null,
                SpecUtils.equalsValue("actorUserId", actorUserId),
                SpecUtils.equalsValue("entityType", entityType));
        var result = repository.findAll(spec, PageRequest.of(page, size, sort)).map(AuditLogDto::from);
        return PageResponse.from(result);
    }
}
