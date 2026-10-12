package com.railmind.booking;

import com.railmind.audit.AuditService;
import com.railmind.auth.User;
import com.railmind.auth.UserRepository;
import com.railmind.booking.BookingEntities.Passenger;
import com.railmind.booking.BookingRepositories.PassengerRepo;
import com.railmind.booking.dto.SavedPassengerDtos.CreateSavedPassengerRequest;
import com.railmind.booking.dto.SavedPassengerDtos.UpdateSavedPassengerRequest;
import com.railmind.common.exceptions.AppExceptions.BadRequestException;
import com.railmind.common.exceptions.AppExceptions.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Same ownership pattern this codebase already locks in for bookings and
 * notifications (findByXAndOwnerUserId, never findById alone) - this test
 * proves update/delete can't touch another user's saved passenger, and that
 * the configured max-saved-passengers limit is actually enforced.
 */
class SavedPassengerServiceOwnershipTest {

    private static final long OWNER_USER_ID = 1L;

    private PassengerRepo passengerRepo;
    private UserRepository userRepository;
    private SavedPassengerService service;

    @BeforeEach
    void setUp() {
        passengerRepo = mock(PassengerRepo.class);
        userRepository = mock(UserRepository.class);
        service = new SavedPassengerService(passengerRepo, userRepository, mock(AuditService.class), 3);
    }

    @Test
    void update_passengerOwnedByAnotherUser_isNotFound() {
        // requireOwned looks up by (id, ownerUserId) - a passenger owned by user 2
        // simply won't be returned when queried with ownerUserId=1, regardless of its real id.
        when(passengerRepo.findByIdAndOwnerUserId(99L, OWNER_USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(OWNER_USER_ID, 99L,
                new UpdateSavedPassengerRequest("Someone", 30, "MALE", "WINDOW")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_passengerOwnedByAnotherUser_isNotFound() {
        when(passengerRepo.findByIdAndOwnerUserId(99L, OWNER_USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(OWNER_USER_ID, 99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_ownedPassenger_softRemovesRatherThanHardDeleting() {
        Passenger passenger = new Passenger();
        passenger.setId(5L);
        passenger.setSaved(true);
        when(passengerRepo.findByIdAndOwnerUserId(5L, OWNER_USER_ID)).thenReturn(Optional.of(passenger));

        service.delete(OWNER_USER_ID, 5L);

        assertThat(passenger.isSaved()).isFalse(); // dropped from the reusable list, not deleted
        verify(passengerRepo).save(passenger);
        verify(passengerRepo, never()).delete(any());
        verify(passengerRepo, never()).deleteById(any());
    }

    @Test
    void create_atConfiguredLimit_isRejected() {
        List<Passenger> threeSaved = List.of(new Passenger(), new Passenger(), new Passenger());
        when(passengerRepo.findByOwnerUserIdAndSavedTrueOrderByFullNameAsc(OWNER_USER_ID)).thenReturn(threeSaved);

        assertThatThrownBy(() -> service.create(OWNER_USER_ID,
                new CreateSavedPassengerRequest("Fourth Passenger", 40, "FEMALE", null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("3");
        verify(passengerRepo, never()).save(any());
    }

    @Test
    void create_underLimit_savesAndOwnsPassenger() {
        when(passengerRepo.findByOwnerUserIdAndSavedTrueOrderByFullNameAsc(OWNER_USER_ID)).thenReturn(List.of());
        User owner = new User();
        owner.setId(OWNER_USER_ID);
        when(userRepository.findById(OWNER_USER_ID)).thenReturn(Optional.of(owner));
        when(passengerRepo.save(any(Passenger.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(OWNER_USER_ID, new CreateSavedPassengerRequest("New Passenger", 25, "MALE", "LOWER"));

        verify(passengerRepo).save(argThat(p -> p.isSaved() && p.getOwnerUser() == owner));
    }
}
