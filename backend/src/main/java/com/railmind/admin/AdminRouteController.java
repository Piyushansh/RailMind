package com.railmind.admin;

import com.railmind.admin.dto.AdminDtos.RouteStopRequest;
import com.railmind.audit.AuditService;
import com.railmind.auth.CurrentUser;
import com.railmind.common.exceptions.AppExceptions.ResourceNotFoundException;
import com.railmind.station.StationRepository;
import com.railmind.train.ScheduleEntities.Route;
import com.railmind.train.ScheduleEntities.RouteStation;
import com.railmind.train.Train;
import com.railmind.train.TrainRepositories.*;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin route-stop management for an EXISTING train (the full provisioning
 * wizard in TrainProvisioningService is the normal way a route first gets
 * created - this controller is for editing it afterwards: adding a stop the
 * admin forgot, correcting a distance, or removing a stop that was wrong).
 */
@RestController
@RequestMapping("/api/v1/admin/routes")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRouteController {

    private final RouteRepo routeRepo;
    private final RouteStationRepo routeStationRepo;
    private final TrainRepo trainRepo;
    private final StationRepository stationRepository;
    private final AuditService auditService;
    private final CurrentUser currentUser;

    public AdminRouteController(RouteRepo routeRepo, RouteStationRepo routeStationRepo, TrainRepo trainRepo,
                                 StationRepository stationRepository, AuditService auditService, CurrentUser currentUser) {
        this.routeRepo = routeRepo;
        this.routeStationRepo = routeStationRepo;
        this.trainRepo = trainRepo;
        this.stationRepository = stationRepository;
        this.auditService = auditService;
        this.currentUser = currentUser;
    }

    public record RouteStopDto(Long id, String stationCode, int sequenceNo, int arrivalOffsetMinutes,
                                int departureOffsetMinutes, int distanceKm) {
        static RouteStopDto from(RouteStation rs) {
            return new RouteStopDto(rs.getId(), rs.getStation().getCode(), rs.getSequenceNo(),
                    rs.getArrivalOffsetMinutes(), rs.getDepartureOffsetMinutes(), rs.getDistanceKm());
        }
    }

    @GetMapping("/trains/{trainId}")
    public List<RouteStopDto> listForTrain(@PathVariable Long trainId) {
        Route route = routeRepo.findByTrainId(trainId)
                .orElseThrow(() -> new ResourceNotFoundException("No route exists for train " + trainId));
        return routeStationRepo.findByRouteIdOrderBySequenceNoAsc(route.getId()).stream().map(RouteStopDto::from).toList();
    }

    @PostMapping("/trains/{trainId}/stops")
    public RouteStopDto addStop(@PathVariable Long trainId, @Valid @RequestBody RouteStopRequest request, Authentication auth) {
        Train train = trainRepo.findById(trainId).orElseThrow(() -> new ResourceNotFoundException("Train not found"));
        Route route = routeRepo.findByTrainId(trainId)
                .orElseThrow(() -> new ResourceNotFoundException("No route exists for train " + trainId));
        var station = stationRepository.findByCode(request.stationCode())
                .orElseThrow(() -> new ResourceNotFoundException("No station with code " + request.stationCode()));

        RouteStation stop = new RouteStation();
        stop.setRoute(route);
        stop.setStation(station);
        stop.setSequenceNo(request.sequenceNo());
        stop.setArrivalOffsetMinutes(request.arrivalOffsetMinutes());
        stop.setDepartureOffsetMinutes(request.departureOffsetMinutes());
        stop.setDistanceKm(request.distanceKm());
        stop = routeStationRepo.save(stop);

        auditService.log(currentUser.resolveId(auth), "ADMIN_ADDED_ROUTE_STOP", "Train", trainId,
                "station=" + request.stationCode() + " seq=" + request.sequenceNo());
        return RouteStopDto.from(stop);
    }

    @PutMapping("/stops/{stopId}")
    public RouteStopDto updateStop(@PathVariable Long stopId, @Valid @RequestBody RouteStopRequest request, Authentication auth) {
        RouteStation stop = routeStationRepo.findById(stopId)
                .orElseThrow(() -> new ResourceNotFoundException("Route stop not found"));
        var station = stationRepository.findByCode(request.stationCode())
                .orElseThrow(() -> new ResourceNotFoundException("No station with code " + request.stationCode()));
        stop.setStation(station);
        stop.setSequenceNo(request.sequenceNo());
        stop.setArrivalOffsetMinutes(request.arrivalOffsetMinutes());
        stop.setDepartureOffsetMinutes(request.departureOffsetMinutes());
        stop.setDistanceKm(request.distanceKm());
        stop = routeStationRepo.save(stop);
        auditService.log(currentUser.resolveId(auth), "ADMIN_UPDATED_ROUTE_STOP", "RouteStop", stopId, null);
        return RouteStopDto.from(stop);
    }

    @DeleteMapping("/stops/{stopId}")
    public void deleteStop(@PathVariable Long stopId, Authentication auth) {
        if (!routeStationRepo.existsById(stopId)) {
            throw new ResourceNotFoundException("Route stop not found");
        }
        routeStationRepo.deleteById(stopId);
        auditService.log(currentUser.resolveId(auth), "ADMIN_DELETED_ROUTE_STOP", "RouteStop", stopId, null);
    }
}
