package com.railmind.admin;

import com.railmind.admin.dto.AdminDtos.PageResponse;
import com.railmind.audit.AuditService;
import com.railmind.auth.CurrentUser;
import com.railmind.common.exceptions.AppExceptions.ConflictException;
import com.railmind.common.exceptions.AppExceptions.ResourceNotFoundException;
import com.railmind.train.ScheduleEntities.TrainSchedule;
import com.railmind.train.SeatEntities.Seat;
import com.railmind.train.SeatEntities.SeatAvailability;
import com.railmind.train.Train;
import com.railmind.train.TrainRepositories.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Admin schedule management for an existing train. Creating a schedule here
 * also generates seat_availability rows for every seat currently on the
 * train's coaches - same rule TrainProvisioningService follows for the
 * initial schedule batch, so an admin-added extra date is just as bookable.
 */
@RestController
@RequestMapping("/api/v1/admin/schedules")
@PreAuthorize("hasRole('ADMIN')")
public class AdminScheduleController {

    private final TrainScheduleRepo scheduleRepo;
    private final TrainRepo trainRepo;
    private final CoachRepo coachRepo;
    private final SeatRepo seatRepo;
    private final SeatAvailabilityRepo seatAvailabilityRepo;
    private final AuditService auditService;
    private final CurrentUser currentUser;

    public AdminScheduleController(TrainScheduleRepo scheduleRepo, TrainRepo trainRepo, CoachRepo coachRepo,
                                    SeatRepo seatRepo, SeatAvailabilityRepo seatAvailabilityRepo,
                                    AuditService auditService, CurrentUser currentUser) {
        this.scheduleRepo = scheduleRepo;
        this.trainRepo = trainRepo;
        this.coachRepo = coachRepo;
        this.seatRepo = seatRepo;
        this.seatAvailabilityRepo = seatAvailabilityRepo;
        this.auditService = auditService;
        this.currentUser = currentUser;
    }

    public record ScheduleAdminDto(Long id, String trainNumber, LocalDate departureDate, LocalTime departureTime,
                                    String status, int delayMinutes, String platform) {
        static ScheduleAdminDto from(TrainSchedule s) {
            return new ScheduleAdminDto(s.getId(), s.getTrain().getNumber(), s.getDepartureDate(), s.getDepartureTime(),
                    s.getStatus(), s.getDelayMinutes(), s.getPlatform());
        }
    }

    public record CreateScheduleRequest(@NotNull Long trainId, @NotNull LocalDate departureDate,
                                         @NotNull LocalTime departureTime) {}

    public record UpdateScheduleRequest(String status, Integer delayMinutes, String platform) {}

    @GetMapping
    public PageResponse<ScheduleAdminDto> list(@RequestParam(required = false) Long trainId,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        var sort = Sort.by(Sort.Direction.ASC, "departureDate");
        var spec = SpecUtils.<TrainSchedule>and(
                trainId != null ? (Specification<TrainSchedule>) (root, q, cb) -> cb.equal(root.get("train").get("id"), trainId) : null,
                SpecUtils.equalsValue("status", status));
        var result = scheduleRepo.findAll(spec, PageRequest.of(page, size, sort)).map(ScheduleAdminDto::from);
        return PageResponse.from(result);
    }

    @Transactional
    @PostMapping
    public ScheduleAdminDto create(@Valid @RequestBody CreateScheduleRequest request, Authentication auth) {
        Train train = trainRepo.findById(request.trainId())
                .orElseThrow(() -> new ResourceNotFoundException("Train not found"));
        if (scheduleRepo.findByTrainIdAndDepartureDate(train.getId(), request.departureDate()).isPresent()) {
            throw new ConflictException("A schedule for this train already exists on " + request.departureDate());
        }
        TrainSchedule schedule = new TrainSchedule();
        schedule.setTrain(train);
        schedule.setDepartureDate(request.departureDate());
        schedule.setDepartureTime(request.departureTime());
        schedule.setStatus("SCHEDULED");
        schedule.setDelayMinutes(0);
        schedule = scheduleRepo.save(schedule);

        for (var coach : coachRepo.findByTrainId(train.getId())) {
            for (Seat seat : seatRepo.findByCoachId(coach.getId())) {
                SeatAvailability availability = new SeatAvailability();
                availability.setSchedule(schedule);
                availability.setSeat(seat);
                availability.setStatus("AVAILABLE");
                seatAvailabilityRepo.save(availability);
            }
        }

        auditService.log(currentUser.resolveId(auth), "ADMIN_CREATED_SCHEDULE", "TrainSchedule", schedule.getId(),
                "train=" + train.getNumber() + " date=" + request.departureDate());
        return ScheduleAdminDto.from(schedule);
    }

    @PatchMapping("/{id}")
    public ScheduleAdminDto update(@PathVariable Long id, @RequestBody UpdateScheduleRequest request, Authentication auth) {
        TrainSchedule schedule = scheduleRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found"));
        if (request.status() != null) schedule.setStatus(request.status());
        if (request.delayMinutes() != null) schedule.setDelayMinutes(request.delayMinutes());
        if (request.platform() != null) schedule.setPlatform(request.platform());
        schedule = scheduleRepo.save(schedule);
        auditService.log(currentUser.resolveId(auth), "ADMIN_UPDATED_SCHEDULE", "TrainSchedule", id,
                "status=" + schedule.getStatus() + " delay=" + schedule.getDelayMinutes());
        return ScheduleAdminDto.from(schedule);
    }
}
