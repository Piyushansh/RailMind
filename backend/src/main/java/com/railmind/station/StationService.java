package com.railmind.station;

import com.railmind.common.exceptions.AppExceptions.ConflictException;
import com.railmind.common.exceptions.AppExceptions.ResourceNotFoundException;
import com.railmind.station.dto.StationDto;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StationService {

    private final StationRepository repository;

    public StationService(StationRepository repository) {
        this.repository = repository;
    }

    /**
     * Cached under "stations" (see RedisConfig for TTL/rationale). Cache key is the
     * query string itself (blank/null queries - "list everything" - are not cached
     * separately per call site but still keyed consistently by Spring's SimpleKeyGenerator).
     */
    @Cacheable(value = "stations", key = "#query == null ? '' : #query")
    public List<StationDto> search(String query) {
        if (query == null || query.isBlank()) {
            return repository.findByActiveTrue().stream().map(StationDto::from).toList();
        }
        return repository.findByCityIgnoreCaseContainingOrNameIgnoreCaseContainingOrCodeIgnoreCaseContaining(
                        query, query, query)
                .stream().filter(Station::isActive).map(StationDto::from).toList();
    }

    public Station getByCodeOrThrow(String code) {
        return repository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("No station found with code " + code));
    }

    public Station getByIdOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No station found with id " + id));
    }

    /**
     * Admin-only mutation. The "stations" cache (see RedisConfig) assumed station data was
     * effectively static demo seed data with a 24h TTL - now that admins can actually add/edit
     * stations, every write must evict the whole cache rather than leaving up to 24h of stale
     * search results after an admin change. allEntries=true because search() caches by query
     * string, so there's no single key to target precisely.
     */
    @CacheEvict(value = "stations", allEntries = true)
    public StationDto create(com.railmind.admin.dto.AdminDtos.CreateStationRequest req) {
        if (repository.findByCode(req.code()).isPresent()) {
            throw new ConflictException("A station with code " + req.code() + " already exists.");
        }
        Station station = new Station();
        station.setCode(req.code().toUpperCase());
        station.setName(req.name());
        station.setCity(req.city());
        station.setState(req.state());
        return StationDto.from(repository.save(station));
    }

    @CacheEvict(value = "stations", allEntries = true)
    public StationDto update(Long id, com.railmind.admin.dto.AdminDtos.UpdateStationRequest req) {
        Station station = getByIdOrThrow(id);
        station.setName(req.name());
        station.setCity(req.city());
        station.setState(req.state());
        return StationDto.from(repository.save(station));
    }

    /** Admin listing: unlike public search(), sees inactive stations too and supports paging/sort. */
    public org.springframework.data.domain.Page<StationDto> adminList(
            String query, Boolean active, org.springframework.data.domain.Pageable pageable) {
        var spec = com.railmind.admin.SpecUtils.<Station>and(
                query != null && !query.isBlank()
                        ? (org.springframework.data.jpa.domain.Specification<Station>) (root, q, cb) -> cb.or(
                                cb.like(cb.lower(root.get("name")), "%" + query.toLowerCase() + "%"),
                                cb.like(cb.lower(root.get("code")), "%" + query.toLowerCase() + "%"),
                                cb.like(cb.lower(root.get("city")), "%" + query.toLowerCase() + "%"))
                        : null,
                com.railmind.admin.SpecUtils.equalsValue("active", active));
        return repository.findAll(spec, pageable).map(StationDto::from);
    }

    @CacheEvict(value = "stations", allEntries = true)
    public StationDto setActive(Long id, boolean active) {
        Station station = getByIdOrThrow(id);
        station.setActive(active);
        return StationDto.from(repository.save(station));
    }
}
