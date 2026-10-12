package com.railmind.admin;

import com.railmind.audit.AuditService;
import com.railmind.auth.CurrentUser;
import com.railmind.common.exceptions.AppExceptions.ResourceNotFoundException;
import com.railmind.train.SeatEntities.Coach;
import com.railmind.train.SeatEntities.Seat;
import com.railmind.train.TrainRepositories.CoachRepo;
import com.railmind.train.TrainRepositories.SeatRepo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin seat viewing for a coach, plus adding an individual seat (e.g. a
 * coach's capacity was corrected upward after creation). Note this does NOT
 * retroactively create seat_availability rows for existing schedules - a
 * seat added after schedules already exist is visible in this endpoint but
 * won't be bookable on those older schedules. This is a known, intentional
 * scope limit given the size of this pass; noted for a follow-up rather
 * than silently left undocumented.
 */
@RestController
@RequestMapping("/api/v1/admin/seats")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSeatController {

    private final SeatRepo seatRepo;
    private final CoachRepo coachRepo;
    private final AuditService auditService;
    private final CurrentUser currentUser;

    public AdminSeatController(SeatRepo seatRepo, CoachRepo coachRepo, AuditService auditService, CurrentUser currentUser) {
        this.seatRepo = seatRepo;
        this.coachRepo = coachRepo;
        this.auditService = auditService;
        this.currentUser = currentUser;
    }

    public record SeatAdminDto(Long id, String coachCode, String seatNumber, String berthType) {
        static SeatAdminDto from(Seat s) {
            return new SeatAdminDto(s.getId(), s.getCoach().getCoachCode(), s.getSeatNumber(), s.getBerthType());
        }
    }

    public record CreateSeatRequest(
            @NotBlank String seatNumber,
            @Pattern(regexp = "LOWER|MIDDLE|UPPER|SIDE_LOWER|SIDE_UPPER") String berthType
    ) {}

    @GetMapping
    public List<SeatAdminDto> listForCoach(@RequestParam Long coachId) {
        return seatRepo.findByCoachId(coachId).stream().map(SeatAdminDto::from).toList();
    }

    @PostMapping
    public SeatAdminDto create(@RequestParam Long coachId, @Valid @RequestBody CreateSeatRequest request, Authentication auth) {
        Coach coach = coachRepo.findById(coachId).orElseThrow(() -> new ResourceNotFoundException("Coach not found"));
        Seat seat = new Seat();
        seat.setCoach(coach);
        seat.setSeatNumber(request.seatNumber());
        seat.setBerthType(request.berthType());
        seat = seatRepo.save(seat);
        auditService.log(currentUser.resolveId(auth), "ADMIN_ADDED_SEAT", "Coach", coachId,
                "seatNumber=" + request.seatNumber());
        return SeatAdminDto.from(seat);
    }
}
