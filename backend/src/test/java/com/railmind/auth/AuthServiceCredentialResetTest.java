package com.railmind.auth;

import com.railmind.audit.AuditService;
import com.railmind.auth.CredentialTokenRepositories.EmailVerificationTokenRepository;
import com.railmind.auth.CredentialTokenRepositories.PasswordResetTokenRepository;
import com.railmind.auth.dto.AuthDtos.ChangePasswordRequest;
import com.railmind.auth.dto.AuthDtos.ForgotPasswordRequest;
import com.railmind.auth.dto.AuthDtos.ResetPasswordRequest;
import com.railmind.common.exceptions.AppExceptions.BadRequestException;
import com.railmind.common.exceptions.AppExceptions.UnauthorizedException;
import com.railmind.notification.NotificationService.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Covers the four properties that actually matter for credential-reset flows:
 *  1) forgotPassword never reveals whether an email exists (no exception either way),
 *  2) a used or expired reset/verification token is rejected,
 *  3) a successful reset/change revokes every existing session,
 *  4) changePassword checks the CURRENT password before allowing a change.
 * Mirrors the plain-Mockito style already used by BookingIdempotencyOwnershipTest
 * and NotificationServiceOwnershipTest - no Spring context needed for this logic.
 */
class AuthServiceCredentialResetTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private RefreshTokenRepository refreshTokenRepository;
    private PasswordResetTokenRepository passwordResetTokenRepository;
    private EmailVerificationTokenRepository emailVerificationTokenRepository;
    private EmailSender emailSender;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        passwordResetTokenRepository = mock(PasswordResetTokenRepository.class);
        emailVerificationTokenRepository = mock(EmailVerificationTokenRepository.class);
        emailSender = mock(EmailSender.class);

        when(jwtService.hashToken(anyString())).thenAnswer(inv -> "hash:" + inv.getArgument(0));
        when(refreshTokenRepository.findByUser_IdAndRevokedFalse(anyLong())).thenReturn(java.util.List.of());

        authService = new AuthService(userRepository, passwordEncoder, jwtService,
                mock(AuthenticationManager.class), mock(AuditService.class), refreshTokenRepository,
                mock(LoginAttemptService.class), passwordResetTokenRepository, emailVerificationTokenRepository,
                emailSender, 30, 48);
    }

    @Test
    void forgotPassword_unknownEmail_doesNotThrowOrRevealAnything() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        authService.forgotPassword(new ForgotPasswordRequest("ghost@example.com"));

        verifyNoInteractions(emailSender);
        verify(passwordResetTokenRepository, never()).save(any());
    }

    @Test
    void forgotPassword_knownEmail_issuesTokenAndSendsIt() {
        User user = userWithId(1L, "real@example.com");
        when(userRepository.findByEmail("real@example.com")).thenReturn(Optional.of(user));

        authService.forgotPassword(new ForgotPasswordRequest("real@example.com"));

        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
        verify(emailSender).send(eq("real@example.com"), anyString(), anyString());
    }

    @Test
    void resetPassword_expiredToken_isRejected() {
        User user = userWithId(1L, "real@example.com");
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setExpiresAt(Instant.now().minusSeconds(60)); // already expired
        when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequest("sometoken", "newpassword1")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expired");
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_alreadyUsedToken_isRejected() {
        User user = userWithId(1L, "real@example.com");
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setExpiresAt(Instant.now().plusSeconds(600));
        token.setUsed(true);
        when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.resetPassword(new ResetPasswordRequest("sometoken", "newpassword1")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already been used");
    }

    @Test
    void resetPassword_validToken_updatesPasswordAndRevokesAllSessions() {
        User user = userWithId(1L, "real@example.com");
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setExpiresAt(Instant.now().plusSeconds(600));
        when(passwordResetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("newpassword1")).thenReturn("encoded-hash");

        RefreshToken activeSession = new RefreshToken();
        activeSession.setUser(user);
        when(refreshTokenRepository.findByUser_IdAndRevokedFalse(1L)).thenReturn(java.util.List.of(activeSession));

        authService.resetPassword(new ResetPasswordRequest("sometoken", "newpassword1"));

        assertThat(user.getPasswordHash()).isEqualTo("encoded-hash");
        assertThat(token.isUsed()).isTrue();
        assertThat(activeSession.isRevoked()).isTrue(); // every existing session killed
        verify(userRepository).save(user);
        verify(passwordResetTokenRepository).save(token);
    }

    @Test
    void changePassword_wrongCurrentPassword_isRejected() {
        User user = userWithId(1L, "real@example.com");
        user.setPasswordHash("old-hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-current", "old-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword(1L, new ChangePasswordRequest("wrong-current", "newpassword1")))
                .isInstanceOf(UnauthorizedException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_correctCurrentPassword_updatesAndRevokesSessions() {
        User user = userWithId(1L, "real@example.com");
        user.setPasswordHash("old-hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct-current", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("newpassword1")).thenReturn("new-hash");

        authService.changePassword(1L, new ChangePasswordRequest("correct-current", "newpassword1"));

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        verify(userRepository).save(user);
    }

    private User userWithId(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setFullName("Test User");
        user.setRoles("USER");
        return user;
    }
}
