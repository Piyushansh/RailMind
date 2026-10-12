package com.railmind.admin;

import com.railmind.admin.dto.AdminDtos.*;
import com.railmind.audit.AuditService;
import com.railmind.auth.CurrentUser;
import com.railmind.train.Fare;
import com.railmind.train.ScheduleEntities.Route;
import com.railmind.train.ScheduleEntities.RouteStation;
import com.railmind.train.SeatEntities.Coach;
import com.railmind.train.Train;
import com.railmind.train.TrainRepositories.*;
import com.railmind.train.TrainSearchService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/trains")
@PreAuthorize("hasRole('ADMIN')")
public class AdminTrainController {

    private final TrainSearchService trainSearchService;
    private final TrainProvisioningService provisioningService;
    private final TrainRepo trainRepo;
    private final RouteRepo routeRepo;
    private final RouteStationRepo routeStationRepo;
    private final CoachRepo coachRepo;
    private final FareRepo fareRepo;
    private final AuditService auditService;
    private final CurrentUser currentUser;

    public AdminTrainController(TrainSearchService trainSearchService, TrainProvisioningService provisioningService,
                                 TrainRepo trainRepo, RouteRepo routeRepo, RouteStationRepo routeStationRepo,
                                 CoachRepo coachRepo, FareRepo fareRepo, AuditService auditService, CurrentUser currentUser) {
        this.trainSearchService = trainSearchService;
        this.provisioningService = provisioningService;
        this.trainRepo = trainRepo;
        this.routeRepo = routeRepo;
        this.routeStationRepo = routeStationRepo;
        this.coachRepo = coachRepo;
        this.fareRepo = fareRepo;
        this.auditService = auditService;
        this.currentUser = currentUser;
    }

    public record TrainAdminDto(Long id, String number, String name, String trainType,
                                 String sourceStationCode, String destinationStationCode, boolean active) {
        static TrainAdminDto from(Train t) {
            return new TrainAdminDto(t.getId(), t.getNumber(), t.getName(), t.getTrainType(),
                    t.getSourceStation().getCode(), t.getDestinationStation().getCode(), t.isActive());
        }
    }

    public record RouteStopDto(String stationCode, int sequenceNo, int arrivalOffsetMinutes,
                                int departureOffsetMinutes, int distanceKm) {
        static RouteStopDto from(RouteStation rs) {
            return new RouteStopDto(rs.getStation().getCode(), rs.getSequenceNo(), rs.getArrivalOffsetMinutes(),
                    rs.getDepartureOffsetMinutes(), rs.getDistanceKm());
        }
    }

    public record CoachSummaryDto(Long id, String coachCode, String travelClass, int totalSeats, boolean active) {
        static CoachSummaryDto from(Coach c) {
            return new CoachSummaryDto(c.getId(), c.getCoachCode(), c.getTravelClass(), c.getTotalSeats(), c.isActive());
        }
    }

    public record FareSummaryDto(Long id, String travelClass, java.math.BigDecimal baseFare, java.math.BigDecimal perKmRate) {
        static FareSummaryDto from(Fare f) {
            return new FareSummaryDto(f.getId(), f.getTravelClass(), f.getBaseFare(), f.getPerKmRate());
        }
    }

    public record TrainDetailDto(TrainAdminDto train, List<RouteStopDto> route,
                                  List<CoachSummaryDto> coaches, List<FareSummaryDto> fares) {}

    @GetMapping
    public PageResponse<TrainAdminDto> list(@RequestParam(required = false) String query,
                                             @RequestParam(required = false) Boolean active,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size,
                                             @RequestParam(defaultValue = "number") String sortBy,
                                             @RequestParam(defaultValue = "asc") String sortDir) {
        var sort = Sort.by(sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
        var spec = SpecUtils.<Train>and(
                query != null && !query.isBlank()
                        ? (Specification<Train>) (root, q, cb) -> cb.or(
                                cb.like(cb.lower(root.get("number")), "%" + query.toLowerCase() + "%"),
                                cb.like(cb.lower(root.get("name")), "%" + query.toLowerCase() + "%"))
                        : null,
                SpecUtils.equalsValue("active", active));
        var result = trainRepo.findAll(spec, PageRequest.of(page, size, sort)).map(TrainAdminDto::from);
        return PageResponse.from(result);
    }

    @GetMapping("/{id}")
    public TrainDetailDto detail(@PathVariable Long id) {
        Train train = trainRepo.findById(id)
                .orElseThrow(() -> new com.railmind.common.exceptions.AppExceptions.ResourceNotFoundException("Train not found"));
        List<RouteStopDto> route = routeRepo.findByTrainId(id)
                .map(r -> routeStationRepo.findByRouteIdOrderBySequenceNoAsc(r.getId()).stream().map(RouteStopDto::from).toList())
                .orElse(List.of());
        List<CoachSummaryDto> coaches = coachRepo.findByTrainId(id).stream().map(CoachSummaryDto::from).toList();
        List<FareSummaryDto> fares = fareRepo.findByTrainId(id).stream().map(FareSummaryDto::from).toList();
        return new TrainDetailDto(TrainAdminDto.from(train), route, coaches, fares);
    }

    /** Legacy path: creates the bare Train row only (no route/coaches/fares/schedule). Kept for
     * backward compatibility; the admin UI's "create train" flow uses POST /full instead, which
     * is the only path that produces an actually bookable train. */
    @PostMapping
    public TrainAdminDto create(@Valid @RequestBody CreateTrainRequest request, Authentication auth) {
        Train train = trainSearchService.createTrain(request);
        auditService.log(currentUser.resolveId(auth), "ADMIN_CREATED_TRAIN", "Train", train.getId(),
                "number=" + train.getNumber());
        return TrainAdminDto.from(train);
    }

    /** The real "create a train" endpoint: route + coaches + seats + fares + a daily schedule,
     * all in one atomic call, so the result is immediately searchable and bookable. */
    @PostMapping("/full")
    public TrainDetailDto createFull(@Valid @RequestBody CreateFullTrainRequest request, Authentication auth) {
        Train train = provisioningService.createFullTrain(request);
        auditService.log(currentUser.resolveId(auth), "ADMIN_CREATED_FULL_TRAIN", "Train", train.getId(),
                "number=" + train.getNumber() + " classes=" + request.coachClasses().size()
                        + " scheduleDays=" + request.scheduleDays());
        return detail(train.getId());
    }

    @PutMapping("/{id}")
    public TrainAdminDto update(@PathVariable Long id, @Valid @RequestBody UpdateTrainRequest request, Authentication auth) {
        Train train = trainSearchService.updateTrain(id, request);
        auditService.log(currentUser.resolveId(auth), "ADMIN_UPDATED_TRAIN", "Train", id, null);
        return TrainAdminDto.from(train);
    }

    @PatchMapping("/{id}/active")
    public TrainAdminDto setActive(@PathVariable Long id, @Valid @RequestBody ActivationRequest request, Authentication auth) {
        Train train = trainRepo.findById(id)
                .orElseThrow(() -> new com.railmind.common.exceptions.AppExceptions.ResourceNotFoundException("Train not found"));
        train.setActive(request.active());
        train = trainRepo.save(train);
        auditService.log(currentUser.resolveId(auth),
                request.active() ? "ADMIN_ACTIVATED_TRAIN" : "ADMIN_DEACTIVATED_TRAIN", "Train", id, null);
        return TrainAdminDto.from(train);
    }
}
