package com.railmind.train;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Coach, Seat and SeatAvailability kept together: they model one physical concept. */
public class SeatEntities {

    @Getter @Setter
    @Entity
    @Table(name = "coaches")
    public static class Coach {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "train_id", nullable = false)
        private Train train;

        @Column(name = "coach_code", nullable = false)
        private String coachCode;

        @Column(name = "travel_class", nullable = false)
        private String travelClass;

        @Column(name = "total_seats", nullable = false)
        private Integer totalSeats;

        @Column(nullable = false)
        private boolean active = true;
    }

    @Getter @Setter
    @Entity
    @Table(name = "seats")
    public static class Seat {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "coach_id", nullable = false)
        private Coach coach;

        @Column(name = "seat_number", nullable = false)
        private String seatNumber;

        @Column(name = "berth_type")
        private String berthType;
    }

    /**
     * One row per (schedule, seat). @Version drives Hibernate's optimistic locking so
     * two concurrent booking attempts for the same seat cannot both succeed silently -
     * the loser gets an OptimisticLockException, which BookingService turns into a
     * SeatUnavailableException / retry.
     */
    @Getter @Setter
    @Entity(name = "SeatAvailabilityEntity")
    @Table(name = "seat_availability")
    public static class SeatAvailability {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "schedule_id", nullable = false)
        private ScheduleEntities.TrainSchedule schedule;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "seat_id", nullable = false)
        private Seat seat;

        @Column(nullable = false)
        private String status = "AVAILABLE"; // AVAILABLE, HELD, BOOKED

        @Version
        private Long version;
    }
}
