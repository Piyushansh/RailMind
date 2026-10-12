package com.railmind.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** All auth-related request/response DTOs, grouped for brevity. */
public class AuthDtos {

    public record RegisterRequest(
            @NotBlank(message = "full name is required") String fullName,
            @NotBlank @Email(message = "a valid email is required") String email,
            @NotBlank @Size(min = 8, message = "password must be at least 8 characters") String password,
            String phone
    ) {}

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {}

    public record RefreshRequest(
            @NotBlank String refreshToken
    ) {}

    public record ForgotPasswordRequest(
            @NotBlank @Email String email
    ) {}

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 8, message = "password must be at least 8 characters") String newPassword
    ) {}

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, message = "password must be at least 8 characters") String newPassword
    ) {}

    public record VerifyEmailRequest(
            @NotBlank String token
    ) {}

    public record UserSummary(
            Long id,
            String fullName,
            String email,
            java.util.List<String> roles
    ) {}

    public record AuthResponse(
            String accessToken,
            String refreshToken,
            long expiresInSeconds,
            UserSummary user
    ) {}
}
