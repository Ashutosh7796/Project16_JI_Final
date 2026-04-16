package com.spring.jwt.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Enterprise-grade security audit log entry.
 * Captures authentication events, token lifecycle, access denials, and config changes.
 * High-write table — no FK constraints, no cascades.
 */
@Entity
@Table(name = "security_audit_log",
        indexes = {
                @Index(name = "idx_audit_event_type", columnList = "event_type"),
                @Index(name = "idx_audit_username", columnList = "username"),
                @Index(name = "idx_audit_created_at", columnList = "created_at"),
                @Index(name = "idx_audit_client_ip", columnList = "client_ip")
        })
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SecurityAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Event type: AUTH_SUCCESS, AUTH_FAILURE, TOKEN_BLACKLIST, TOKEN_REUSE,
     * ACCESS_DENIED, LOGOUT, PERMISSION_CHANGE, CONFIG_CHANGE
     */
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "username")
    private String username;

    @Column(name = "client_ip", length = 45)
    private String clientIp;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "request_uri", length = 1024)
    private String requestUri;

    @Column(name = "request_method", length = 10)
    private String requestMethod;

    /** Masked jti if relevant */
    @Column(name = "token_id")
    private String tokenId;

    /** Additional context — reason, resource, required authority, etc. */
    @Column(name = "detail", length = 2000)
    private String detail;

    @Column(name = "success", nullable = false)
    private boolean success = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /**
     * Builder-style factory for common event types.
     */
    public static SecurityAuditLog of(String eventType, String username, boolean success) {
        SecurityAuditLog log = new SecurityAuditLog();
        log.eventType = eventType;
        log.username = username;
        log.success = success;
        log.createdAt = Instant.now();
        return log;
    }
}
