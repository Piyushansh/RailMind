package com.railmind.station;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.List;
import java.util.Optional;

public interface StationRepository extends JpaRepository<Station, Long>, JpaSpecificationExecutor<Station> {
    Optional<Station> findByCode(String code);
    List<Station> findByCityIgnoreCaseContainingOrNameIgnoreCaseContainingOrCodeIgnoreCaseContaining(
            String city, String name, String code);
    List<Station> findByActiveTrue();
}
