package com.railmind.booking.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class SavedPassengerDtos {

    public record SavedPassengerDto(Long id, String fullName, int age, String gender, String seatPreference) {}

    public record CreateSavedPassengerRequest(
            @NotBlank String fullName,
            @Min(0) @Max(120) int age,
            @NotBlank String gender,
            String seatPreference
    ) {}

    public record UpdateSavedPassengerRequest(
            @NotBlank String fullName,
            @Min(0) @Max(120) int age,
            @NotBlank String gender,
            String seatPreference
    ) {}
}
