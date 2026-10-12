package com.railmind.train;

import com.railmind.station.Station;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Route, RouteStation and TrainSchedule kept in one file since they are small,
 * tightly related entities that are always read together when computing a journey.
 */
public class ScheduleEntities {

    @Getter @Setter
    @Entity(name = "TrainRoute")
    @Table(name = "routes")
    public static class Route {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "train_id", nullable = false)
        private Train train;
    }

    @Getter @Setter
    @Entity(name = "TrainRouteStation")
    @Table(name = "route_stations")
    public static class RouteStation {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "route_id", nullable = false)
        private Route route;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "station_id", nullable = false)
        private Station station;

        @Column(name = "sequence_no", nullable = false)
        private Integer sequenceNo;

        @Column(name = "arrival_offset_minutes", nullable = false)
        private Integer arrivalOffsetMinutes;

        @Column(name = "departure_offset_minutes", nullable = false)
        private Integer departureOffsetMinutes;

        @Column(name = "distance_km", nullable = false)
        private Integer distanceKm;
    }

    @Getter @Setter
    @Entity(name = "TrainScheduleEntity")
    @Table(name = "train_schedules")
    public static class TrainSchedule {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "train_id", nullable = false)
        private Train train;

        @Column(name = "departure_date", nullable = false)
        private LocalDate departureDate;

        @Column(name = "departure_time", nullable = false)
        private LocalTime departureTime;

        @Column(nullable = false)
        private String status = "SCHEDULED";

        @Column(name = "delay_minutes", nullable = false)
        private Integer delayMinutes = 0;

        private String platform;

        @Column(name = "created_at", nullable = false, updatable = false)
        private Instant createdAt;

        @Column(name = "updated_at", nullable = false)
        private Instant updatedAt;

        @PrePersist
        void onCreate() { createdAt = Instant.now(); updatedAt = createdAt; }

        @PreUpdate
        void onUpdate() { updatedAt = Instant.now(); }
    }
}
