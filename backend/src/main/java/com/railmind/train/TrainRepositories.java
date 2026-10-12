package com.railmind.train;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TrainRepositories {

    interface TrainRepo extends JpaRepository<Train, Long>, JpaSpecificationExecutor<Train> {
        Optional<Train> findByNumber(String number);
    }

    interface RouteRepo extends JpaRepository<ScheduleEntities.Route, Long> {
        Optional<ScheduleEntities.Route> findByTrainId(Long trainId);
    }

    interface RouteStationRepo extends JpaRepository<ScheduleEntities.RouteStation, Long> {
        List<ScheduleEntities.RouteStation> findByRouteIdOrderBySequenceNoAsc(Long routeId);
    }

    interface TrainScheduleRepo extends JpaRepository<ScheduleEntities.TrainSchedule, Long>,
            JpaSpecificationExecutor<ScheduleEntities.TrainSchedule> {
        Optional<ScheduleEntities.TrainSchedule> findByTrainIdAndDepartureDate(Long trainId, LocalDate date);
        List<ScheduleEntities.TrainSchedule> findByTrainIdOrderByDepartureDateAsc(Long trainId);

        /**
         * Core search query: finds every train whose route passes through both stations
         * (source before destination) with a schedule on the given date.
         */
        @Query("""
            SELECT ts FROM TrainScheduleEntity ts
            JOIN TrainRoute r ON r.train = ts.train
            JOIN TrainRouteStation src ON src.route = r AND src.station.id = :sourceStationId
            JOIN TrainRouteStation dst ON dst.route = r AND dst.station.id = :destinationStationId
            WHERE ts.departureDate = :date
              AND src.sequenceNo < dst.sequenceNo
              AND ts.train.active = true
            ORDER BY ts.departureTime ASC
            """)
        List<ScheduleEntities.TrainSchedule> searchByRoute(@Param("sourceStationId") Long sourceStationId,
                                                             @Param("destinationStationId") Long destinationStationId,
                                                             @Param("date") LocalDate date);
    }

    interface CoachRepo extends JpaRepository<SeatEntities.Coach, Long>, JpaSpecificationExecutor<SeatEntities.Coach> {
        List<SeatEntities.Coach> findByTrainId(Long trainId);
        List<SeatEntities.Coach> findByTrainIdAndTravelClass(Long trainId, String travelClass);
    }

    interface SeatRepo extends JpaRepository<SeatEntities.Seat, Long> {
        List<SeatEntities.Seat> findByCoachId(Long coachId);
    }

    interface SeatAvailabilityRepo extends JpaRepository<SeatEntities.SeatAvailability, Long> {
        long countByScheduleIdAndSeat_Coach_TravelClassAndStatus(Long scheduleId, String travelClass, String status);

        List<SeatEntities.SeatAvailability> findByScheduleIdAndSeat_Coach_TravelClassAndStatus(
                Long scheduleId, String travelClass, String status);

        @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
        @Query("SELECT sa FROM SeatAvailabilityEntity sa WHERE sa.id = :id")
        Optional<SeatEntities.SeatAvailability> findByIdForUpdate(@Param("id") Long id);
    }

    interface FareRepo extends JpaRepository<Fare, Long> {
        Optional<Fare> findByTrainIdAndTravelClass(Long trainId, String travelClass);
        List<Fare> findByTrainId(Long trainId);
    }
}
