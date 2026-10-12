package com.railmind.admin;

import com.railmind.admin.dto.AdminDtos.ActivationRequest;
import com.railmind.admin.dto.AdminDtos.PageResponse;
import com.railmind.audit.AuditService;
import com.railmind.auth.CurrentUser;
import com.railmind.auth.User;
import com.railmind.auth.UserRepository;
import com.railmind.common.exceptions.AppExceptions.ConflictException;
import com.railmind.common.exceptions.AppExceptions.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Admin-only user management: search/filter/paginate, block/unblock
 * (User.enabled), and role assignment. Deliberately does NOT expose
 * passwordHash anywhere - UserAdminDto never includes it.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private static final Set<String> VALID_ROLES = Set.of("USER", "ADMIN", "SUPPORT_AGENT");

    private final UserRepository userRepository;
    private final AuditService auditService;
    private final CurrentUser currentUser;

    public AdminUserController(UserRepository userRepository, AuditService auditService, CurrentUser currentUser) {
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.currentUser = currentUser;
    }

    public record UserAdminDto(Long id, String fullName, String email, String phone,
                                List<String> roles, boolean emailVerified, boolean enabled) {
        static UserAdminDto from(User u) {
            return new UserAdminDto(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(),
                    Arrays.stream(u.getRoles().split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList(),
                    u.isEmailVerified(), u.isEnabled());
        }
    }

    public record SetRolesRequest(@NotEmpty List<String> roles) {}

    @GetMapping
    public PageResponse<UserAdminDto> list(@RequestParam(required = false) String query,
                                            @RequestParam(required = false) Boolean enabled,
                                            @RequestParam(required = false) String role,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size,
                                            @RequestParam(defaultValue = "fullName") String sortBy,
                                            @RequestParam(defaultValue = "asc") String sortDir) {
        var sort = Sort.by(sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
        var spec = SpecUtils.<User>and(
                query != null && !query.isBlank()
                        ? (Specification<User>) (root, q, cb) -> cb.or(
                                cb.like(cb.lower(root.get("fullName")), "%" + query.toLowerCase() + "%"),
                                cb.like(cb.lower(root.get("email")), "%" + query.toLowerCase() + "%"))
                        : null,
                SpecUtils.equalsValue("enabled", enabled),
                role != null && !role.isBlank()
                        ? (Specification<User>) (root, q, cb) -> cb.like(root.get("roles"), "%" + role + "%")
                        : null);
        var result = userRepository.findAll(spec, PageRequest.of(page, size, sort)).map(UserAdminDto::from);
        return PageResponse.from(result);
    }

    @GetMapping("/{id}")
    public UserAdminDto detail(@PathVariable Long id) {
        return UserAdminDto.from(userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found")));
    }

    @PatchMapping("/{id}/enabled")
    public UserAdminDto setEnabled(@PathVariable Long id, @Valid @RequestBody ActivationRequest request, Authentication auth) {
        Long actorId = currentUser.resolveId(auth);
        if (id.equals(actorId) && !request.active()) {
            throw new ConflictException("You cannot block your own admin account.");
        }
        User user = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setEnabled(request.active());
        user = userRepository.save(user);
        auditService.log(actorId, request.active() ? "ADMIN_UNBLOCKED_USER" : "ADMIN_BLOCKED_USER",
                "User", id, "email=" + user.getEmail());
        return UserAdminDto.from(user);
    }

    @PutMapping("/{id}/roles")
    public UserAdminDto setRoles(@PathVariable Long id, @Valid @RequestBody SetRolesRequest request, Authentication auth) {
        Long actorId = currentUser.resolveId(auth);
        List<String> normalized = request.roles().stream().map(String::trim).map(String::toUpperCase).distinct().toList();
        for (String r : normalized) {
            if (!VALID_ROLES.contains(r)) {
                throw new ConflictException("Unknown role: " + r + ". Valid roles are " + VALID_ROLES);
            }
        }
        if (id.equals(actorId) && !normalized.contains("ADMIN")) {
            throw new ConflictException("You cannot remove your own ADMIN role.");
        }
        User user = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        String oldRoles = user.getRoles();
        user.setRoles(String.join(",", normalized));
        user = userRepository.save(user);
        auditService.log(actorId, "ADMIN_CHANGED_USER_ROLES", "User", id,
                "from=[" + oldRoles + "] to=[" + user.getRoles() + "]");
        return UserAdminDto.from(user);
    }
}
