package com.railmind.booking;

import com.railmind.auth.User;
import com.railmind.station.Station;
import com.railmind.train.ScheduleEntities.TrainSchedule;
import com.railmind.train.SeatEntities.Coach;
import com.railmind.train.SeatEntities.Seat;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class BookingEntities {

    @Getter @Setter
    @Entity
    @Table(name = "passengers")
    public static class Passenger {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "full_name", nullable = false)
        private String fullName;

        @Column(nullable = false)
        private Integer age;

        @Column(nullable = false)
        private String gender;

        /** Null for passengers entered ad hoc during a single booking; set when the user saves this passenger for reuse. */
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "owner_user_id")
        private User ownerUser;

        @Column(name = "seat_preference")
        private String seatPreference; // LOWER, MIDDLE, UPPER, SIDE_LOWER, SIDE_UPPER, WINDOW, AISLE

        @Column(name = "is_saved", nullable = false)
        private boolean saved = false;
    }

    @Getter @Setter
    @Entity
    @Table(name = "bookings")
    public static class Booking {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "user_id", nullable = false)
        private User user;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "schedule_id", nullable = false)
        private TrainSchedule schedule;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "source_station_id", nullable = false)
        private Station sourceStation;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "destination_station_id", nullable = false)
        private Station destinationStation;

        @Column(name = "travel_class", nullable = false)
        private String travelClass;

        @Column(nullable = false)
        private String status = "PENDING_PAYMENT"; // PENDING_PAYMENT, CONFIRMED, CANCELLED, EXPIRED

        @Column(name = "total_fare", nullable = false)
        private BigDecimal totalFare;

        @Column(name = "idempotency_key", unique = true)
        private String idempotencyKey;

        /** When the seat hold on this booking expires if payment hasn't completed by then. Null once CONFIRMED/CANCELLED. */
        @Column(name = "hold_expires_at")
        private Instant holdExpiresAt;

        @Column(name = "trip_guardian_enabled", nullable = false)
        private boolean tripGuardianEnabled = false;

        /** Set when this booking was created to replace one cancelled due to a disruption. */
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "rebooked_from_booking_id")
        private Booking rebookedFromBooking;

        @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
        private List<BookingPassenger> passengers = new ArrayList<>();

        @Column(name = "created_at", nullable = false, updatable = false)
        private Instant createdAt;

        @Column(name = "updated_at", nullable = false)
        private Instant updatedAt;

        @PrePersist
        void onCreate() { createdAt = Instant.now(); updatedAt = createdAt; }

        @PreUpdate
        void onUpdate() { updatedAt = Instant.now(); }
    }

    @Getter @Setter
    @Entity
    @Table(name = "booking_passengers")
    public static class BookingPassenger {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "booking_id", nullable = false)
        private Booking booking;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "passenger_id", nullable = false)
        private Passenger passenger;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "seat_id")
        private Seat seat;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "coach_id")
        private Coach coach;

        @Column(name = "berth_preference")
        private String berthPreference;
    }

    @Getter @Setter
    @Entity
    @Table(name = "pnrs")
    public static class Pnr {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @OneToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "booking_id", nullable = false, unique = true)
        private Booking booking;

        @Column(name = "pnr_number", nullable = false, unique = true)
        private String pnrNumber;

        @Column(name = "created_at", nullable = false, updatable = false)
        private Instant createdAt;

        @PrePersist
        void onCreate() { createdAt = Instant.now(); }
    }
}
