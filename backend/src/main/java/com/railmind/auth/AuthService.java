package com.railmind.auth;

import com.railmind.audit.AuditService;
import com.railmind.auth.CredentialTokenRepositories.EmailVerificationTokenRepository;
import com.railmind.auth.CredentialTokenRepositories.PasswordResetTokenRepository;
import com.railmind.auth.dto.AuthDtos.*;
import com.railmind.common.exceptions.AppExceptions.*;
import com.railmind.notification.NotificationService.EmailSender;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

/**
 * Handles registration, login, refresh-token rotation, and logout.
 *
 * Refresh tokens are rotated on every use: each successful /refresh call
 * revokes the presented token and issues a brand new one, recording the
 * rotation chain (replacedByHash). If a refresh token is presented that is
 * already revoked, that's a strong signal of token theft/reuse - we treat it
 * as such and revoke the entire chain for that user rather than silently
 * allowing it.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final AuditService auditService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginAttemptService loginAttemptService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final EmailSender emailSender;
    private final SecureRandom secureRandom = new SecureRandom();

    private final long passwordResetTtlMinutes;
    private final long emailVerificationTtlHours;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                        AuthenticationManager authenticationManager, AuditService auditService,
                        RefreshTokenRepository refreshTokenRepository, LoginAttemptService loginAttemptService,
                        PasswordResetTokenRepository passwordResetTokenRepository,
                        EmailVerificationTokenRepository emailVerificationTokenRepository,
                        EmailSender emailSender,
                        @Value("${railmind.auth.password-reset-ttl-minutes:30}") long passwordResetTtlMinutes,
                        @Value("${railmind.auth.email-verification-ttl-hours:48}") long emailVerificationTtlHours) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.auditService = auditService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.loginAttemptService = loginAttemptService;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.emailSender = emailSender;
        this.passwordResetTtlMinutes = passwordResetTtlMinutes;
        this.emailVerificationTtlHours = emailVerificationTtlHours;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new ConflictException("An account with this email already exists.");
        }
        User user = new User();
        user.setFullName(req.fullName());
        user.setEmail(req.email().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setPhone(req.phone());
        user.setRoles("USER"); // roles are never taken from the request body
        user = userRepository.save(user);

        auditService.log(user.getId(), "USER_REGISTERED", "User", user.getId(), null);
        issueEmailVerificationToken(user);
        return issueNewTokenPair(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        String email = req.email().toLowerCase();
        if (loginAttemptService.isLocked(email)) {
            throw new UnauthorizedException("Too many failed login attempts. Please try again in a few minutes.");
        }

        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, req.password()));
        } catch (org.springframework.security.core.AuthenticationException ex) {
            loginAttemptService.recordFailure(email);
            throw ex;
        }
        loginAttemptService.recordSuccess(email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password."));

        auditService.log(user.getId(), "USER_LOGIN", "User", user.getId(), null);
        return issueNewTokenPair(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest req) {
        Claims claims;
        try {
            claims = jwtService.parseClaims(req.refreshToken());
        } catch (JwtException | IllegalArgumentException ex) {
            throw new UnauthorizedException("Refresh token is invalid or expired.");
        }
        if (!jwtService.isRefreshToken(claims)) {
            throw new UnauthorizedException("Token supplied is not a refresh token.");
        }

        User user = userRepository.findByEmail(claims.getSubject())
                .orElseThrow(() -> new UnauthorizedException("Account no longer exists."));

        String presentedHash = jwtService.hashToken(req.refreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenHash(presentedHash).orElse(null);

        if (stored == null) {
            throw new UnauthorizedException("Refresh token is not recognized.");
        }
        if (stored.isRevoked()) {
            // Reuse of an already-rotated/revoked token: treat as possible theft and kill every
            // active session for this user rather than trusting the presented token further.
            revokeAllTokensForUser(user.getId());
            auditService.log(user.getId(), "REFRESH_TOKEN_REUSE_DETECTED", "User", user.getId(), null);
            throw new UnauthorizedException("This refresh token has already been used. All sessions have been revoked - please log in again.");
        }
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException("Refresh token has expired.");
        }

        AuthResponse response = issueNewTokenPair(user);

        // rotate: mark the old token revoked and link it to whatever replaced it
        stored.setRevoked(true);
        stored.setReplacedByHash(jwtService.hashToken(response.refreshToken()));
        refreshTokenRepository.save(stored);

        return response;
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return; // nothing to revoke; client just discards its access token
        }
        String hash = jwtService.hashToken(refreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
            auditService.log(token.getUser().getId(), "USER_LOGOUT", "User", token.getUser().getId(), null);
        });
    }

    private void revokeAllTokensForUser(Long userId) {
        refreshTokenRepository.findByUser_IdAndRevokedFalse(userId)
                .forEach(t -> { t.setRevoked(true); refreshTokenRepository.save(t); });
    }

    // ---- password reset ------------------------------------------------

    /**
     * Always succeeds from the caller's point of view, whether or not the email
     * exists - this prevents account enumeration via the forgot-password form.
     * If the account does exist, a single-use reset token is generated, its hash
     * persisted, and the raw token "emailed" (logged, via the mock EmailSender)
     * to the user.
     */
    @Transactional
    public void forgotPassword(ForgotPasswordRequest req) {
        String email = req.email().toLowerCase();
        userRepository.findByEmail(email).ifPresent(user -> {
            String rawToken = generateOpaqueToken();
            PasswordResetToken token = new PasswordResetToken();
            token.setUser(user);
            token.setTokenHash(jwtService.hashToken(rawToken));
            token.setExpiresAt(Instant.now().plus(passwordResetTtlMinutes, ChronoUnit.MINUTES));
            passwordResetTokenRepository.save(token);

            auditService.log(user.getId(), "PASSWORD_RESET_REQUESTED", "User", user.getId(), null);
            emailSender.send(user.getEmail(), "Reset your RailMind password",
                    "Use this token to reset your password (expires in " + passwordResetTtlMinutes
                            + " minutes): " + rawToken);
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        String hash = jwtService.hashToken(req.token());
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BadRequestException("Reset token is invalid."));

        if (token.isUsed()) {
            throw new BadRequestException("This reset token has already been used.");
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("Reset token has expired. Please request a new one.");
        }

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);

        token.setUsed(true);
        passwordResetTokenRepository.save(token);

        // A password reset is a strong signal to kill every existing session.
        revokeAllTokensForUser(user.getId());

        auditService.log(user.getId(), "PASSWORD_RESET_COMPLETED", "User", user.getId(), null);
        emailSender.send(user.getEmail(), "Your RailMind password was changed",
                "Your password was just reset. If this wasn't you, contact support immediately.");
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Account no longer exists."));

        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Current password is incorrect.");
        }

        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);
        revokeAllTokensForUser(user.getId());

        auditService.log(user.getId(), "PASSWORD_CHANGED", "User", user.getId(), null);
        emailSender.send(user.getEmail(), "Your RailMind password was changed",
                "Your password was just changed. If this wasn't you, contact support immediately.");
    }

    // ---- email verification --------------------------------------------

    private void issueEmailVerificationToken(User user) {
        String rawToken = generateOpaqueToken();
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setTokenHash(jwtService.hashToken(rawToken));
        token.setExpiresAt(Instant.now().plus(emailVerificationTtlHours, ChronoUnit.HOURS));
        emailVerificationTokenRepository.save(token);

        emailSender.send(user.getEmail(), "Verify your RailMind email",
                "Use this token to verify your email (expires in " + emailVerificationTtlHours
                        + " hours): " + rawToken);
    }

    @Transactional
    public void resendVerification(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Account no longer exists."));
        if (user.isEmailVerified()) {
            throw new BadRequestException("This email is already verified.");
        }
        issueEmailVerificationToken(user);
    }

    @Transactional
    public void verifyEmail(VerifyEmailRequest req) {
        String hash = jwtService.hashToken(req.token());
        EmailVerificationToken token = emailVerificationTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BadRequestException("Verification token is invalid."));

        if (token.isUsed()) {
            throw new BadRequestException("This verification token has already been used.");
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("Verification token has expired. Please request a new one.");
        }

        User user = token.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        token.setUsed(true);
        emailVerificationTokenRepository.save(token);

        auditService.log(user.getId(), "EMAIL_VERIFIED", "User", user.getId(), null);
    }

    /** 256 bits of randomness, URL-safe base64 - opaque, not a JWT, since these tokens have exactly one purpose each. */
    private String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private AuthResponse issueNewTokenPair(User user) {
        String access = jwtService.generateAccessToken(user);
        String refresh = jwtService.generateRefreshToken(user);

        RefreshToken record = new RefreshToken();
        record.setUser(user);
        record.setTokenHash(jwtService.hashToken(refresh));
        record.setExpiresAt(jwtService.refreshTokenExpiry());
        refreshTokenRepository.save(record);

        UserSummary summary = new UserSummary(user.getId(), user.getFullName(), user.getEmail(), user.roleList());
        return new AuthResponse(access, refresh, jwtService.accessTokenTtlSeconds(), summary);
    }
}
