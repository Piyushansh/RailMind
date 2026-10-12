package com.railmind.booking;

import com.railmind.audit.AuditService;
import com.railmind.auth.UserRepository;
import com.railmind.booking.BookingEntities.Passenger;
import com.railmind.booking.BookingRepositories.PassengerRepo;
import com.railmind.booking.dto.SavedPassengerDtos.*;
import com.railmind.common.exceptions.AppExceptions.BadRequestException;
import com.railmind.common.exceptions.AppExceptions.ResourceNotFoundException;
import com.railmind.common.exceptions.AppExceptions.UnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * A user's reusable passenger list, distinct from the booking-scoped passenger
 * rows created inline during checkout. A saved passenger can be referenced by
 * id (PassengerRequest.savedPassengerId) when creating a booking instead of
 * retyping their details every time.
 */
@Service
public class SavedPassengerService {

    private final PassengerRepo passengerRepo;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final int maxSavedPassengers;

    public SavedPassengerService(PassengerRepo passengerRepo, UserRepository userRepository,
                                  AuditService auditService,
                                  @Value("${railmind.passenger.max-saved:10}") int maxSavedPassengers) {
        this.passengerRepo = passengerRepo;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.maxSavedPassengers = maxSavedPassengers;
    }

    @Transactional(readOnly = true)
    public List<SavedPassengerDto> list(Long userId) {
        return passengerRepo.findByOwnerUserIdAndSavedTrueOrderByFullNameAsc(userId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public SavedPassengerDto create(Long userId, CreateSavedPassengerRequest req) {
        long existing = passengerRepo.findByOwnerUserIdAndSavedTrueOrderByFullNameAsc(userId).size();
        if (existing >= maxSavedPassengers) {
            throw new BadRequestException("You can save at most " + maxSavedPassengers + " passengers.");
        }
        Passenger passenger = new Passenger();
        passenger.setFullName(req.fullName());
        passenger.setAge(req.age());
        passenger.setGender(req.gender());
        passenger.setSeatPreference(req.seatPreference());
        passenger.setSaved(true);
        passenger.setOwnerUser(userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Account no longer exists.")));
        passenger = passengerRepo.save(passenger);

        auditService.log(userId, "SAVED_PASSENGER_CREATED", "Passenger", passenger.getId(), null);
        return toDto(passenger);
    }

    @Transactional
    public SavedPassengerDto update(Long userId, Long passengerId, UpdateSavedPassengerRequest req) {
        Passenger passenger = requireOwned(userId, passengerId);
        passenger.setFullName(req.fullName());
        passenger.setAge(req.age());
        passenger.setGender(req.gender());
        passenger.setSeatPreference(req.seatPreference());
        passengerRepo.save(passenger);

        auditService.log(userId, "SAVED_PASSENGER_UPDATED", "Passenger", passenger.getId(), null);
        return toDto(passenger);
    }

    @Transactional
    public void delete(Long userId, Long passengerId) {
        Passenger passenger = requireOwned(userId, passengerId);
        // Never hard-delete a passenger that's attached to a real booking history - just
        // drop it from the reusable list. Existing bookings keep their passenger row intact.
        passenger.setSaved(false);
        passengerRepo.save(passenger);
        auditService.log(userId, "SAVED_PASSENGER_REMOVED", "Passenger", passenger.getId(), null);
    }

    private Passenger requireOwned(Long userId, Long passengerId) {
        return passengerRepo.findByIdAndOwnerUserId(passengerId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Saved passenger not found."));
    }

    private SavedPassengerDto toDto(Passenger p) {
        return new SavedPassengerDto(p.getId(), p.getFullName(), p.getAge(), p.getGender(), p.getSeatPreference());
    }
}
