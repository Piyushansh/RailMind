package com.railmind.admin;

import com.railmind.admin.dto.AdminDtos.*;
import com.railmind.audit.AuditService;
import com.railmind.auth.CurrentUser;
import com.railmind.station.StationService;
import com.railmind.station.dto.StationDto;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Admin-only station management. Protected both by the path rule in
 * SecurityConfig (/api/v1/admin/** -> hasRole("ADMIN")) and by an explicit
 * @PreAuthorize here - defense in depth, so a future path-rule refactor
 * can't silently drop this endpoint's protection.
 */
@RestController
@RequestMapping("/api/v1/admin/stations")
@PreAuthorize("hasRole('ADMIN')")
public class AdminStationController {

    private final StationService stationService;
    private final AuditService auditService;
    private final CurrentUser currentUser;

    public AdminStationController(StationService stationService, AuditService auditService, CurrentUser currentUser) {
        this.stationService = stationService;
        this.auditService = auditService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public PageResponse<StationDto> list(@RequestParam(required = false) String query,
                                          @RequestParam(required = false) Boolean active,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size,
                                          @RequestParam(defaultValue = "name") String sortBy,
                                          @RequestParam(defaultValue = "asc") String sortDir) {
        var sort = Sort.by(sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
        return PageResponse.from(stationService.adminList(query, active, PageRequest.of(page, size, sort)));
    }

    @PostMapping
    public StationDto create(@Valid @RequestBody CreateStationRequest request, Authentication auth) {
        StationDto created = stationService.create(request);
        auditService.log(currentUser.resolveId(auth), "ADMIN_CREATED_STATION", "Station", created.id(),
                "code=" + created.code());
        return created;
    }

    @PutMapping("/{id}")
    public StationDto update(@PathVariable Long id, @Valid @RequestBody UpdateStationRequest request, Authentication auth) {
        StationDto updated = stationService.update(id, request);
        auditService.log(currentUser.resolveId(auth), "ADMIN_UPDATED_STATION", "Station", id, null);
        return updated;
    }

    @PatchMapping("/{id}/active")
    public StationDto setActive(@PathVariable Long id, @Valid @RequestBody ActivationRequest request, Authentication auth) {
        StationDto updated = stationService.setActive(id, request.active());
        auditService.log(currentUser.resolveId(auth),
                request.active() ? "ADMIN_ACTIVATED_STATION" : "ADMIN_DEACTIVATED_STATION", "Station", id, null);
        return updated;
    }
}
