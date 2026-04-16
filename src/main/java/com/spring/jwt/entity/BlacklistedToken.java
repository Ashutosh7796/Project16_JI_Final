package com.spring.jwt.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Persisted record of a blacklisted JWT token.
 * Survives server restarts and works across multiple pod deployments.
 */
@Entity
@Table(name = "blacklisted_tokens",
        indexes = {
                @Index(name = "idx_blacklisted_expires", columnList = "expires_at")
        })
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BlacklistedToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** JWT jti claim — unique identifier of the blacklisted token */
    @Column(name = "token_id", nullable = false, unique = true)
    private String tokenId;

    /** "access" or "refresh" */
    @Column(name = "token_type", nullable = false, length = 20)
    private String tokenType = "unknown";

    @Column(name = "username")
    private String username;

    /** Why blacklisted: logout, token_rotation, security_revoke */
    @Column(name = "reason", nullable = false, length = 100)
    private String reason = "token_rotation";

    /** When the original JWT expires — safe to purge after this time */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "blacklisted_at", nullable = false)
    private Instant blacklistedAt;

    @Column(name = "reuse_attempts", nullable = false)
    private int reuseAttempts = 0;

    public BlacklistedToken(String tokenId, String tokenType, String username,
                            String reason, Instant expiresAt) {
        this.tokenId = tokenId;
        this.tokenType = tokenType;
        this.username = username;
        this.reason = reason;
        this.expiresAt = expiresAt;
        this.blacklistedAt = Instant.now();
    }
}
