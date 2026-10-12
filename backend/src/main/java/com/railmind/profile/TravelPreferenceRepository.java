package com.railmind.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TravelPreferenceRepository extends JpaRepository<TravelPreference, Long> {
    Optional<TravelPreference> findByUserId(Long userId);
}
