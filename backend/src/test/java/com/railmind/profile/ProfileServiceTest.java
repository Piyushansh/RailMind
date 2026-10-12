package com.railmind.profile;

import com.railmind.audit.AuditService;
import com.railmind.auth.User;
import com.railmind.auth.UserRepository;
import com.railmind.profile.dto.ProfileDtos.TravelPreferenceDto;
import com.railmind.profile.dto.ProfileDtos.UpdateTravelPreferenceRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ProfileServiceTest {

    private TravelPreferenceRepository preferenceRepository;
    private UserRepository userRepository;
    private ProfileService service;

    @BeforeEach
    void setUp() {
        preferenceRepository = mock(TravelPreferenceRepository.class);
        userRepository = mock(UserRepository.class);
        service = new ProfileService(userRepository, preferenceRepository, mock(AuditService.class));
    }

    @Test
    void getPreferences_userNeverSetAny_returnsSafeDefaultsRatherThanThrowing() {
        when(preferenceRepository.findByUserId(1L)).thenReturn(Optional.empty());

        TravelPreferenceDto dto = service.getPreferences(1L);

        assertThat(dto.preferredClass()).isNull();
        assertThat(dto.notifyEmail()).isTrue();   // sensible opt-in default
        assertThat(dto.notifyDelay()).isTrue();
        assertThat(dto.preferFastest()).isFalse();
    }

    @Test
    void updatePreferences_firstTime_createsRowOwnedByUser() {
        User user = new User();
        user.setId(1L);
        when(preferenceRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(preferenceRepository.save(any(TravelPreference.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateTravelPreferenceRequest req = new UpdateTravelPreferenceRequest(
                "AC3", "LOWER", new BigDecimal("2000"), true, false, true, true, true, false, true);

        TravelPreferenceDto dto = service.updatePreferences(1L, req);

        assertThat(dto.preferredClass()).isEqualTo("AC3");
        assertThat(dto.preferFastest()).isTrue();
        assertThat(dto.tripGuardianDefaultEnabled()).isTrue();
        verify(preferenceRepository).save(argThat(p -> p.getUser() == user));
    }

    @Test
    void updatePreferences_existingRow_updatesInPlaceRatherThanCreatingASecondRow() {
        TravelPreference existing = new TravelPreference();
        existing.setId(42L);
        when(preferenceRepository.findByUserId(1L)).thenReturn(Optional.of(existing));
        when(preferenceRepository.save(any(TravelPreference.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateTravelPreferenceRequest req = new UpdateTravelPreferenceRequest(
                "SL", null, null, false, true, false, false, false, false, false);

        service.updatePreferences(1L, req);

        verify(preferenceRepository).save(existing);
        assertThat(existing.getPreferredClass()).isEqualTo("SL");
        // findById(user) should never be needed on the update-existing path
        verify(userRepository, never()).findById(any());
    }
}
