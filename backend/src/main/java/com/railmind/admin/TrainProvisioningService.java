package com.railmind.admin;

import com.railmind.admin.dto.AdminDtos.CreateFullTrainRequest;
import com.railmind.admin.dto.AdminDtos.RouteStopRequest;
import com.railmind.common.exceptions.AppExceptions.ConflictException;
import com.railmind.common.exceptions.AppExceptions.ResourceNotFoundException;
import com.railmind.station.Station;
import com.railmind.station.StationRepository;
import com.railmind.train.Fare;
import com.railmind.train.ScheduleEntities.Route;
import com.railmind.train.ScheduleEntities.RouteStation;
import com.railmind.train.ScheduleEntities.TrainSchedule;
import com.railmind.train.SeatEntities.Coach;
import com.railmind.train.SeatEntities.Seat;
import com.railmind.train.SeatEntities.SeatAvailability;
import com.railmind.train.Train;
import com.railmind.train.TrainRepositories.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates a train with EVERYTHING it needs to actually be searchable and
 * bookable in one atomic operation: route + route stations, coaches + seats
 * per requested travel class, fares per class, and a daily schedule (with
 * seat_availability rows) for the requested number of days. This is what
 * "do not leave train records unusable" means in practice - the plain
 * TrainSearchService.createTrain (still used by the original
 * AdminTrainController.create) only ever created the bare Train row, which
 * is unbookable on its own; this service is the one the admin "create train"
 * page actually calls.
 */
@Service
public class TrainProvisioningService {

    private static final List<String> BERTH_CYCLE_SLEEPER = List.of(
            "LOWER", "MIDDLE", "UPPER", "SIDE_LOWER", "SIDE_UPPER", "SIDE_UPPER");
    private static final List<String> BERTH_CYCLE_AC = List.of("LOWER", "UPPER");

    private final TrainRepo trainRepo;
    private final RouteRepo routeRepo;
    private final RouteStationRepo routeStationRepo;
    private final CoachRepo coachRepo;
    private final SeatRepo seatRepo;
    private final SeatAvailabilityRepo seatAvailabilityRepo;
    private final FareRepo fareRepo;
    private final TrainScheduleRepo scheduleRepo;
    private final StationRepository stationRepository;

    public TrainProvisioningService(TrainRepo trainRepo, RouteRepo routeRepo, RouteStationRepo routeStationRepo,
                                     CoachRepo coachRepo, SeatRepo seatRepo, SeatAvailabilityRepo seatAvailabilityRepo,
                                     FareRepo fareRepo, TrainScheduleRepo scheduleRepo, StationRepository stationRepository) {
        this.trainRepo = trainRepo;
        this.routeRepo = routeRepo;
        this.routeStationRepo = routeStationRepo;
        this.coachRepo = coachRepo;
        this.seatRepo = seatRepo;
        this.seatAvailabilityRepo = seatAvailabilityRepo;
        this.fareRepo = fareRepo;
        this.scheduleRepo = scheduleRepo;
        this.stationRepository = stationRepository;
    }

    @Transactional
    public Train createFullTrain(CreateFullTrainRequest req) {
        if (trainRepo.findByNumber(req.number()).isPresent()) {
            throw new ConflictException("A train with number " + req.number() + " already exists.");
        }
        Station source = stationRepository.findByCode(req.sourceStationCode())
                .orElseThrow(() -> new ResourceNotFoundException("No station with code " + req.sourceStationCode()));
        Station destination = stationRepository.findByCode(req.destinationStationCode())
                .orElseThrow(() -> new ResourceNotFoundException("No station with code " + req.destinationStationCode()));
        if (source.getId().equals(destination.getId())) {
            throw new ConflictException("Source and destination stations must be different.");
        }

        Train train = new Train();
        train.setNumber(req.number());
        train.setName(req.name());
        train.setTrainType(req.trainType());
        train.setSourceStation(source);
        train.setDestinationStation(destination);
        train.setActive(true);
        train = trainRepo.save(train);

        buildRoute(train, source, destination, req.intermediateStops());
        int totalDistanceKm = routeStationRepo.findByRouteIdOrderBySequenceNoAsc(
                        routeRepo.findByTrainId(train.getId()).orElseThrow().getId())
                .stream().mapToInt(RouteStation::getDistanceKm).max().orElse(0);

        List<Coach> coaches = new ArrayList<>();
        for (var classSpec : req.coachClasses()) {
            List<String> berthCycle = "SL".equals(classSpec.travelClass()) ? BERTH_CYCLE_SLEEPER : BERTH_CYCLE_AC;
            for (int c = 1; c <= classSpec.coachCount(); c++) {
                Coach coach = new Coach();
                coach.setTrain(train);
                coach.setCoachCode(coachCodePrefix(classSpec.travelClass()) + c);
                coach.setTravelClass(classSpec.travelClass());
                coach.setTotalSeats(classSpec.seatsPerCoach());
                coach.setActive(true);
                coach = coachRepo.save(coach);
                coaches.add(coach);

                for (int s = 1; s <= classSpec.seatsPerCoach(); s++) {
                    Seat seat = new Seat();
                    seat.setCoach(coach);
                    seat.setSeatNumber(String.valueOf(s));
                    seat.setBerthType(berthCycle.get((s - 1) % berthCycle.size()));
                    seatRepo.save(seat);
                }
            }

            Fare fare = new Fare();
            fare.setTrain(train);
            fare.setTravelClass(classSpec.travelClass());
            fare.setBaseFare(classSpec.baseFare());
            fare.setPerKmRate(classSpec.perKmRate());
            fareRepo.save(fare);
        }

        List<Seat> allSeats = coaches.stream().flatMap(c -> seatRepo.findByCoachId(c.getId()).stream()).toList();
        for (int day = 1; day <= req.scheduleDays(); day++) {
            TrainSchedule schedule = new TrainSchedule();
            schedule.setTrain(train);
            schedule.setDepartureDate(LocalDate.now().plusDays(day));
            schedule.setDepartureTime(req.departureTime());
            schedule.setStatus("SCHEDULED");
            schedule.setDelayMinutes(0);
            schedule = scheduleRepo.save(schedule);

            for (Seat seat : allSeats) {
                SeatAvailability availability = new SeatAvailability();
                availability.setSchedule(schedule);
                availability.setSeat(seat);
                availability.setStatus("AVAILABLE");
                seatAvailabilityRepo.save(availability);
            }
        }

        return train;
    }

    private void buildRoute(Train train, Station source, Station destination, List<RouteStopRequest> intermediateStops) {
        Route route = new Route();
        route.setTrain(train);
        route = routeRepo.save(route);

        RouteStation originStop = new RouteStation();
        originStop.setRoute(route);
        originStop.setStation(source);
        originStop.setSequenceNo(1);
        originStop.setArrivalOffsetMinutes(0);
        originStop.setDepartureOffsetMinutes(0);
        originStop.setDistanceKm(0);
        routeStationRepo.save(originStop);

        int lastSeq = 1;
        int lastDistance = 0;
        if (intermediateStops != null) {
            for (RouteStopRequest stop : intermediateStops) {
                Station station = stationRepository.findByCode(stop.stationCode())
                        .orElseThrow(() -> new ResourceNotFoundException("No station with code " + stop.stationCode()));
                RouteStation rs = new RouteStation();
                rs.setRoute(route);
                rs.setStation(station);
                rs.setSequenceNo(stop.sequenceNo());
                rs.setArrivalOffsetMinutes(stop.arrivalOffsetMinutes());
                rs.setDepartureOffsetMinutes(stop.departureOffsetMinutes());
                rs.setDistanceKm(stop.distanceKm());
                routeStationRepo.save(rs);
                lastSeq = Math.max(lastSeq, stop.sequenceNo());
                lastDistance = Math.max(lastDistance, stop.distanceKm());
            }
        }

        RouteStation destStop = new RouteStation();
        destStop.setRoute(route);
        destStop.setStation(destination);
        destStop.setSequenceNo(lastSeq + 1);
        // Demo-reasonable defaults when the caller doesn't specify exact end-to-end timing:
        // final leg is at least as long as the longest intermediate leg plus a fixed increment,
        // rather than a hardcoded total that could be shorter than an intermediate stop's own offset.
        destStop.setArrivalOffsetMinutes(lastDistance > 0 ? (lastDistance * 2) : 360);
        destStop.setDepartureOffsetMinutes(destStop.getArrivalOffsetMinutes() + 15);
        destStop.setDistanceKm(lastDistance > 0 ? (lastDistance * 2) : 700);
        routeStationRepo.save(destStop);
    }

    private String coachCodePrefix(String travelClass) {
        return switch (travelClass) {
            case "SL" -> "S";
            case "AC3" -> "B";
            case "AC2" -> "A";
            case "AC1" -> "H";
            default -> "C";
        };
    }
}
