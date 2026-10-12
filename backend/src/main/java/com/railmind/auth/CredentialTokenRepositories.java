package com.railmind.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Repositories for password-reset and email-verification tokens, grouped for brevity. */
public interface CredentialTokenRepositories {

    public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
        Optional<PasswordResetToken> findByTokenHash(String tokenHash);
    }

    public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {
        Optional<EmailVerificationToken> findByTokenHash(String tokenHash);
    }
}
