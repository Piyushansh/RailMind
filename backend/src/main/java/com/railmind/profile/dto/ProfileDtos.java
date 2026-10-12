package com.railmind.profile.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.List;

public class ProfileDtos {

    public record ProfileDto(
            Long id,
            String fullName,
            String email,
            String phone,
            boolean emailVerified,
            List<String> roles
    ) {}

    public record UpdateProfileRequest(
            @NotBlank(message = "full name is required") String fullName,
            String phone
    ) {}

    public record TravelPreferenceDto(
            String preferredClass,
            String preferredBerth,
            BigDecimal budgetMax,
            boolean preferFastest,
            boolean preferCheapest,
            boolean preferOvernight,
            boolean notifyEmail,
            boolean notifyDelay,
            boolean notifyPromotions,
            boolean tripGuardianDefaultEnabled
    ) {}

    public record UpdateTravelPreferenceRequest(
            String preferredClass,
            String preferredBerth,
            @DecimalMin(value = "0.0", inclusive = true, message = "budget cannot be negative") BigDecimal budgetMax,
            boolean preferFastest,
            boolean preferCheapest,
            boolean preferOvernight,
            boolean notifyEmail,
            boolean notifyDelay,
            boolean notifyPromotions,
            boolean tripGuardianDefaultEnabled
    ) {}
}
