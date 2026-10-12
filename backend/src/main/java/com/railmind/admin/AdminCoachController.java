package com.railmind.admin;

import com.railmind.admin.dto.AdminDtos.ActivationRequest;
import com.railmind.audit.AuditService;
import com.railmind.auth.CurrentUser;
import com.railmind.common.exceptions.AppExceptions.ConflictException;
import com.railmind.common.exceptions.AppExceptions.ResourceNotFoundException;
import com.railmind.train.SeatEntities.Coach;
import com.railmind.train.SeatEntities.Seat;
import com.railmind.train.Train;
import com.railmind.train.TrainRepositories.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin coach management for an existing train. Adding a coach here also
 * generates its seats immediately (same rule TrainProvisioningService
 * follows) - a coach with zero seats would be unusable, same principle as
 * "do not leave train records unusable" applied one level down.
 */
@RestController
@RequestMapping("/api/v1/admin/coaches")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCoachController {

    private static final List<String> BERTH_CYCLE_SLEEPER = List.of(
            "LOWER", "MIDDLE", "UPPER", "SIDE_LOWER", "SIDE_UPPER", "SIDE_UPPER");
    private static final List<String> BERTH_CYCLE_AC = List.of("LOWER", "UPPER");

    private final CoachRepo coachRepo;
    private final SeatRepo seatRepo;
    private final TrainRepo trainRepo;
    private final AuditService auditService;
    private final CurrentUser currentUser;

    public AdminCoachController(CoachRepo coachRepo, SeatRepo seatRepo, TrainRepo trainRepo,
                                 AuditService auditService, CurrentUser currentUser) {
        this.coachRepo = coachRepo;
        this.seatRepo = seatRepo;
        this.trainRepo = trainRepo;
        this.auditService = auditService;
        this.currentUser = currentUser;
    }

    public record CoachAdminDto(Long id, String coachCode, String travelClass, int totalSeats, boolean active) {
        static CoachAdminDto from(Coach c) {
            return new CoachAdminDto(c.getId(), c.getCoachCode(), c.getTravelClass(), c.getTotalSeats(), c.isActive());
        }
    }

    public record CreateCoachRequest(
            @NotBlank String coachCode,
            @Pattern(regexp = "SL|AC3|AC2|AC1") String travelClass,
            @Min(1) @Max(120) int seatCount
    ) {}

    @GetMapping
    public List<CoachAdminDto> listForTrain(@RequestParam Long trainId) {
        return coachRepo.findByTrainId(trainId).stream().map(CoachAdminDto::from).toList();
    }

    @Transactional
    @PostMapping
    public CoachAdminDto create(@RequestParam Long trainId, @Valid @RequestBody CreateCoachRequest request, Authentication auth) {
        Train train = trainRepo.findById(trainId).orElseThrow(() -> new ResourceNotFoundException("Train not found"));
        boolean duplicateCode = coachRepo.findByTrainId(trainId).stream()
                .anyMatch(c -> c.getCoachCode().equalsIgnoreCase(request.coachCode()));
        if (duplicateCode) {
            throw new ConflictException("Train " + train.getNumber() + " already has a coach " + request.coachCode());
        }
        Coach coach = new Coach();
        coach.setTrain(train);
        coach.setCoachCode(request.coachCode());
        coach.setTravelClass(request.travelClass());
        coach.setTotalSeats(request.seatCount());
        coach.setActive(true);
        coach = coachRepo.save(coach);

        List<String> berthCycle = "SL".equals(request.travelClass()) ? BERTH_CYCLE_SLEEPER : BERTH_CYCLE_AC;
        for (int s = 1; s <= request.seatCount(); s++) {
            Seat seat = new Seat();
            seat.setCoach(coach);
            seat.setSeatNumber(String.valueOf(s));
            seat.setBerthType(berthCycle.get((s - 1) % berthCycle.size()));
            seatRepo.save(seat);
        }

        auditService.log(currentUser.resolveId(auth), "ADMIN_ADDED_COACH", "Train", trainId,
                "coach=" + request.coachCode() + " class=" + request.travelClass() + " seats=" + request.seatCount());
        return CoachAdminDto.from(coach);
    }

    @PatchMapping("/{id}/active")
    public CoachAdminDto setActive(@PathVariable Long id, @Valid @RequestBody ActivationRequest request, Authentication auth) {
        Coach coach = coachRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Coach not found"));
        coach.setActive(request.active());
        coach = coachRepo.save(coach);
        auditService.log(currentUser.resolveId(auth),
                request.active() ? "ADMIN_ACTIVATED_COACH" : "ADMIN_DEACTIVATED_COACH", "Coach", id, null);
        return CoachAdminDto.from(coach);
    }
}
