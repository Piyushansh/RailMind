package com.railmind.profile;

import com.railmind.audit.AuditService;
import com.railmind.auth.User;
import com.railmind.auth.UserRepository;
import com.railmind.common.exceptions.AppExceptions.UnauthorizedException;
import com.railmind.profile.dto.ProfileDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Own-account profile fields (name/phone) and travel preferences. Email and
 * password are deliberately not editable here - email changes would need
 * re-verification and password changes go through AuthService.changePassword,
 * which additionally revokes sessions.
 */
@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final TravelPreferenceRepository preferenceRepository;
    private final AuditService auditService;

    public ProfileService(UserRepository userRepository, TravelPreferenceRepository preferenceRepository,
                           AuditService auditService) {
        this.userRepository = userRepository;
        this.preferenceRepository = preferenceRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public ProfileDto getProfile(Long userId) {
        return toProfileDto(requireUser(userId));
    }

    @Transactional
    public ProfileDto updateProfile(Long userId, UpdateProfileRequest req) {
        User user = requireUser(userId);
        user.setFullName(req.fullName());
        user.setPhone(req.phone());
        userRepository.save(user);
        auditService.log(userId, "PROFILE_UPDATED", "User", userId, null);
        return toProfileDto(user);
    }

    @Transactional(readOnly = true)
    public TravelPreferenceDto getPreferences(Long userId) {
        return preferenceRepository.findByUserId(userId)
                .map(this::toPreferenceDto)
                .orElseGet(this::defaultPreferenceDto);
    }

    @Transactional
    public TravelPreferenceDto updatePreferences(Long userId, UpdateTravelPreferenceRequest req) {
        TravelPreference pref = preferenceRepository.findByUserId(userId).orElseGet(() -> {
            TravelPreference p = new TravelPreference();
            p.setUser(requireUser(userId));
            return p;
        });
        pref.setPreferredClass(req.preferredClass());
        pref.setPreferredBerth(req.preferredBerth());
        pref.setBudgetMax(req.budgetMax());
        pref.setPreferFastest(req.preferFastest());
        pref.setPreferCheapest(req.preferCheapest());
        pref.setPreferOvernight(req.preferOvernight());
        pref.setNotifyEmail(req.notifyEmail());
        pref.setNotifyDelay(req.notifyDelay());
        pref.setNotifyPromotions(req.notifyPromotions());
        pref.setTripGuardianDefaultEnabled(req.tripGuardianDefaultEnabled());
        preferenceRepository.save(pref);
        auditService.log(userId, "PREFERENCES_UPDATED", "TravelPreference", pref.getId(), null);
        return toPreferenceDto(pref);
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Account no longer exists."));
    }

    private ProfileDto toProfileDto(User user) {
        return new ProfileDto(user.getId(), user.getFullName(), user.getEmail(), user.getPhone(),
                user.isEmailVerified(), user.roleList());
    }

    private TravelPreferenceDto toPreferenceDto(TravelPreference p) {
        return new TravelPreferenceDto(p.getPreferredClass(), p.getPreferredBerth(), p.getBudgetMax(),
                p.isPreferFastest(), p.isPreferCheapest(), p.isPreferOvernight(),
                p.isNotifyEmail(), p.isNotifyDelay(), p.isNotifyPromotions(), p.isTripGuardianDefaultEnabled());
    }

    private TravelPreferenceDto defaultPreferenceDto() {
        return new TravelPreferenceDto(null, null, null, false, false, false, true, true, false, false);
    }
}
