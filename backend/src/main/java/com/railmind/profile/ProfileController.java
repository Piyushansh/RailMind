package com.railmind.profile;

import com.railmind.auth.CurrentUser;
import com.railmind.profile.dto.ProfileDtos.*;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

    private final ProfileService profileService;
    private final CurrentUser currentUser;

    public ProfileController(ProfileService profileService, CurrentUser currentUser) {
        this.profileService = profileService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ProfileDto getProfile(Authentication auth) {
        return profileService.getProfile(currentUser.resolveId(auth));
    }

    @PutMapping
    public ProfileDto updateProfile(@Valid @RequestBody UpdateProfileRequest request, Authentication auth) {
        return profileService.updateProfile(currentUser.resolveId(auth), request);
    }

    @GetMapping("/preferences")
    public TravelPreferenceDto getPreferences(Authentication auth) {
        return profileService.getPreferences(currentUser.resolveId(auth));
    }

    @PutMapping("/preferences")
    public TravelPreferenceDto updatePreferences(@Valid @RequestBody UpdateTravelPreferenceRequest request, Authentication auth) {
        return profileService.updatePreferences(currentUser.resolveId(auth), request);
    }
}
