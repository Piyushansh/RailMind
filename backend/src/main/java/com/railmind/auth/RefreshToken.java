package com.railmind.auth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

/**
 * Server-side record of an issued refresh token, keyed by a SHA-256 hash of
 * the token itself (never the raw value). This is what makes rotation and
 * revocation possible for an otherwise-stateless JWT: a refresh token is only
 * honoured if its hash exists here, is unrevoked, and hasn't expired.
 */
@Getter
@Setter
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(name = "replaced_by_hash")
    private String replacedByHash;

    @PrePersist
    void onCreate() {
        if (issuedAt == null) issuedAt = Instant.now();
    }
}
