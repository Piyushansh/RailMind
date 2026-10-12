package com.railmind.profile;

import com.railmind.auth.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One row per user. Feeds two consumers: normal search/ranking (as the default
 * sort/filter when a user doesn't specify one) and the AI journey-planning tool
 * (getUserPreferences), so "find me the best train" can fall back to a user's
 * standing preferences instead of asking them to repeat themselves every time.
 */
@Getter
@Setter
@Entity
@Table(name = "travel_preferences")
public class TravelPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "preferred_class")
    private String preferredClass; // SL, AC3, AC2, AC1, CC

    @Column(name = "preferred_berth")
    private String preferredBerth; // LOWER, MIDDLE, UPPER, SIDE_LOWER, SIDE_UPPER, WINDOW, AISLE

    @Column(name = "budget_max")
    private BigDecimal budgetMax;

    @Column(name = "prefer_fastest", nullable = false)
    private boolean preferFastest = false;

    @Column(name = "prefer_cheapest", nullable = false)
    private boolean preferCheapest = false;

    @Column(name = "prefer_overnight", nullable = false)
    private boolean preferOvernight = false;

    @Column(name = "notify_email", nullable = false)
    private boolean notifyEmail = true;

    @Column(name = "notify_delay", nullable = false)
    private boolean notifyDelay = true;

    @Column(name = "notify_promotions", nullable = false)
    private boolean notifyPromotions = false;

    @Column(name = "trip_guardian_default_enabled", nullable = false)
    private boolean tripGuardianDefaultEnabled = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
