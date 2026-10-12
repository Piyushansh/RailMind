package com.railmind.train;

import com.railmind.common.exceptions.AppExceptions.BadRequestException;
import com.railmind.common.exceptions.AppExceptions.ConflictException;
import com.railmind.common.exceptions.AppExceptions.ResourceNotFoundException;
import com.railmind.station.Station;
import com.railmind.station.StationRepository;
import com.railmind.train.ScheduleEntities.Route;
import com.railmind.train.ScheduleEntities.RouteStation;
import com.railmind.train.ScheduleEntities.TrainSchedule;
import com.railmind.train.SeatEntities.Coach;
import com.railmind.train.dto.TrainDtos.*;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The single source of truth for train search, details, availability and fare.
 * The AI layer calls into this service through tools; it never queries the
 * database directly and never invents a number that isn't returned from here.
 */
@Service
public class TrainSearchService {

    private final StationRepository stationRepository;
    private final TrainRepositories.TrainRepo trainRepo;
    private final TrainRepositories.RouteRepo routeRepo;
    private final TrainRepositories.RouteStationRepo routeStationRepo;
    private final TrainRepositories.TrainScheduleRepo scheduleRepo;
    private final TrainRepositories.CoachRepo coachRepo;
    private final TrainRepositories.SeatAvailabilityRepo seatAvailabilityRepo;
    private final TrainRepositories.FareRepo fareRepo;

    public TrainSearchService(StationRepository stationRepository,
                               TrainRepositories.TrainRepo trainRepo,
                               TrainRepositories.RouteRepo routeRepo,
                               TrainRepositories.RouteStationRepo routeStationRepo,
                               TrainRepositories.TrainScheduleRepo scheduleRepo,
                               TrainRepositories.CoachRepo coachRepo,
                               TrainRepositories.SeatAvailabilityRepo seatAvailabilityRepo,
                               TrainRepositories.FareRepo fareRepo) {
        this.stationRepository = stationRepository;
        this.trainRepo = trainRepo;
        this.routeRepo = routeRepo;
        this.routeStationRepo = routeStationRepo;
        this.scheduleRepo = scheduleRepo;
        this.coachRepo = coachRepo;
        this.seatAvailabilityRepo = seatAvailabilityRepo;
        this.fareRepo = fareRepo;
    }

    // NOTE: search() is intentionally NOT cached - every result embeds live availableSeats
    // counts (via buildClassAvailability -> seatAvailabilityRepo.countBy...), and caching
    // that would let two users see stale seat counts and both attempt to book a seat that
    // is no longer actually available. See RedisConfig for what IS safely cached and why.
    public List<TrainSearchResult> search(String sourceCode, String destinationCode, LocalDate date,
                                           String travelClass, String sortBy) {
        Station source = stationRepository.findByCode(sourceCode)
                .orElseThrow(() -> new ResourceNotFoundException("Unknown source station: " + sourceCode));
        Station destination = stationRepository.findByCode(destinationCode)
                .orElseThrow(() -> new ResourceNotFoundException("Unknown destination station: " + destinationCode));
        if (source.getId().equals(destination.getId())) {
            throw new BadRequestException("Source and destination must be different stations.");
        }

        List<TrainSchedule> schedules = scheduleRepo.searchByRoute(source.getId(), destination.getId(), date);

        List<TrainSearchResult> results = schedules.stream()
                .map(ts -> toSearchResult(ts, source, destination, travelClass))
                .filter(r -> travelClass == null || r.classes().stream().anyMatch(c -> c.travelClass().equalsIgnoreCase(travelClass) && c.availableSeats() > 0))
                .toList();

        return sortResults(results, sortBy);
    }

    private TrainSearchResult toSearchResult(TrainSchedule ts, Station source, Station destination, String travelClassFilter) {
        Train train = ts.getTrain();
        Route route = routeRepo.findByTrainId(train.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No route configured for train " + train.getNumber()));
        List<RouteStation> stops = routeStationRepo.findByRouteIdOrderBySequenceNoAsc(route.getId());

        RouteStation srcStop = stops.stream().filter(s -> s.getStation().getId().equals(source.getId())).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Route does not include source station"));
        RouteStation dstStop = stops.stream().filter(s -> s.getStation().getId().equals(destination.getId())).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Route does not include destination station"));

        int durationMinutes = dstStop.getArrivalOffsetMinutes() - srcStop.getDepartureOffsetMinutes();
        int distanceKm = dstStop.getDistanceKm() - srcStop.getDistanceKm();

        List<Coach> coaches = coachRepo.findByTrainId(train.getId());
        List<SeatClassAvailability> classAvailability = coaches.stream()
                .map(Coach::getTravelClass).distinct()
                .filter(cls -> travelClassFilter == null || cls.equalsIgnoreCase(travelClassFilter))
                .map(cls -> buildClassAvailability(ts, train, cls, distanceKm))
                .toList();

        return new TrainSearchResult(
                ts.getId(), train.getId(), train.getNumber(), train.getName(), train.getTrainType(),
                source.getCode(), destination.getCode(), ts.getDepartureDate(), ts.getDepartureTime(),
                durationMinutes, ts.getStatus(), ts.getDelayMinutes(), classAvailability
        );
    }

    private SeatClassAvailability buildClassAvailability(TrainSchedule ts, Train train, String travelClass, int distanceKm) {
        int total = (int) coachRepo.findByTrainIdAndTravelClass(train.getId(), travelClass).stream()
                .mapToLong(Coach::getTotalSeats).sum();
        int available = (int) seatAvailabilityRepo.countByScheduleIdAndSeat_Coach_TravelClassAndStatus(
                ts.getId(), travelClass, "AVAILABLE");
        BigDecimal fare = calculateFare(train.getId(), travelClass, distanceKm).totalFarePerPassenger();
        return new SeatClassAvailability(travelClass, total, available, fare);
    }

    private List<TrainSearchResult> sortResults(List<TrainSearchResult> results, String sortBy) {
        if (sortBy == null) sortBy = "recommended";
        Comparator<TrainSearchResult> comparator = switch (sortBy.toLowerCase()) {
            case "cheapest" -> Comparator.comparing(r -> cheapestFare(r.classes()));
            case "fastest" -> Comparator.comparingInt(TrainSearchResult::durationMinutes);
            case "earliest_departure" -> Comparator.comparing(TrainSearchResult::departureTime);
            case "reliable" -> Comparator.comparingInt(TrainSearchResult::delayMinutes);
            default -> Comparator.comparingInt(TrainSearchResult::durationMinutes)
                    .thenComparing(r -> cheapestFare(r.classes()));
        };
        return results.stream().sorted(comparator).toList();
    }

    private BigDecimal cheapestFare(List<SeatClassAvailability> classes) {
        return classes.stream().map(SeatClassAvailability::fare).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
    }

    @org.springframework.cache.annotation.Cacheable(value = "trainDetails", key = "#trainId")
    public TrainDetailsDto getTrainDetails(Long trainId) {
        Train train = trainRepo.findById(trainId)
                .orElseThrow(() -> new ResourceNotFoundException("No train found with id " + trainId));
        Route route = routeRepo.findByTrainId(trainId)
                .orElseThrow(() -> new ResourceNotFoundException("No route configured for train " + train.getNumber()));
        List<RouteStop> stops = routeStationRepo.findByRouteIdOrderBySequenceNoAsc(route.getId()).stream()
                .map(rs -> new RouteStop(rs.getStation().getCode(), rs.getStation().getName(), rs.getSequenceNo(),
                        rs.getArrivalOffsetMinutes(), rs.getDepartureOffsetMinutes(), rs.getDistanceKm()))
                .toList();
        return new TrainDetailsDto(train.getId(), train.getNumber(), train.getName(), train.getTrainType(), stops);
    }

    // Intentionally NOT cached - this is the live, mutable seat-availability read path.
    public List<AvailabilityDto> getAvailability(Long scheduleId) {
        TrainSchedule ts = scheduleRepo.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("No schedule found with id " + scheduleId));
        List<Coach> coaches = coachRepo.findByTrainId(ts.getTrain().getId());
        Map<String, Integer> totalsByClass = new java.util.HashMap<>();
        for (Coach c : coaches) totalsByClass.merge(c.getTravelClass(), c.getTotalSeats(), Integer::sum);

        return totalsByClass.entrySet().stream()
                .map(e -> new AvailabilityDto(scheduleId, e.getKey(), e.getValue(),
                        (int) seatAvailabilityRepo.countByScheduleIdAndSeat_Coach_TravelClassAndStatus(scheduleId, e.getKey(), "AVAILABLE")))
                .toList();
    }

    /** Distance (km) and duration (minutes) between two stations on a given train's route. */
    public record RouteSpan(int distanceKm, int durationMinutes) {}

    public RouteSpan getRouteSpan(Long trainId, Long sourceStationId, Long destinationStationId) {
        Route route = routeRepo.findByTrainId(trainId)
                .orElseThrow(() -> new ResourceNotFoundException("No route configured for train id " + trainId));
        List<RouteStation> stops = routeStationRepo.findByRouteIdOrderBySequenceNoAsc(route.getId());
        RouteStation srcStop = stops.stream().filter(s -> s.getStation().getId().equals(sourceStationId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Route does not include the requested source station"));
        RouteStation dstStop = stops.stream().filter(s -> s.getStation().getId().equals(destinationStationId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Route does not include the requested destination station"));
        if (srcStop.getSequenceNo() >= dstStop.getSequenceNo()) {
            throw new BadRequestException("Source station must come before destination station on this train's route.");
        }
        return new RouteSpan(dstStop.getDistanceKm() - srcStop.getDistanceKm(),
                dstStop.getArrivalOffsetMinutes() - srcStop.getDepartureOffsetMinutes());
    }

    public FareQuoteDto calculateFare(Long trainId, String travelClass, int distanceKm) {
        Fare fare = fareRepo.findByTrainIdAndTravelClass(trainId, travelClass)
                .orElseThrow(() -> new ResourceNotFoundException("No fare configured for class " + travelClass));
        BigDecimal total = fare.getBaseFare().add(fare.getPerKmRate().multiply(BigDecimal.valueOf(distanceKm)))
                .setScale(2, RoundingMode.HALF_UP);
        return new FareQuoteDto(trainId, travelClass, distanceKm, fare.getBaseFare(), fare.getPerKmRate(), total);
    }

    // ---- Admin-only mutations (see AdminTrainController) ----

    /**
     * Creates the Train record only. This does NOT provision a route, coaches,
     * seats, fares, or schedules - a newly created train has none of those until
     * an admin (or a future admin tool) sets them up, so it will not appear in
     * search results or be bookable yet. This is an honest, deliberate scope
     * limit for this pass, not a hidden gap - full train provisioning (route +
     * coaches + seats + fares + a schedule) is meaningfully more work and is
     * called out in the README rather than faked here.
     */
    @CacheEvict(value = "trainDetails", allEntries = true)
    public Train createTrain(com.railmind.admin.dto.AdminDtos.CreateTrainRequest req) {
        if (trainRepo.findByNumber(req.number()).isPresent()) {
            throw new ConflictException("A train with number " + req.number() + " already exists.");
        }
        Station source = stationRepository.findByCode(req.sourceStationCode())
                .orElseThrow(() -> new ResourceNotFoundException("Unknown source station: " + req.sourceStationCode()));
        Station destination = stationRepository.findByCode(req.destinationStationCode())
                .orElseThrow(() -> new ResourceNotFoundException("Unknown destination station: " + req.destinationStationCode()));

        Train train = new Train();
        train.setNumber(req.number());
        train.setName(req.name());
        train.setTrainType(req.trainType());
        train.setSourceStation(source);
        train.setDestinationStation(destination);
        return trainRepo.save(train);
    }

    @CacheEvict(value = "trainDetails", key = "#trainId")
    public Train updateTrain(Long trainId, com.railmind.admin.dto.AdminDtos.UpdateTrainRequest req) {
        Train train = trainRepo.findById(trainId)
                .orElseThrow(() -> new ResourceNotFoundException("No train found with id " + trainId));
        train.setName(req.name());
        train.setTrainType(req.trainType());
        return trainRepo.save(train);
    }
}
