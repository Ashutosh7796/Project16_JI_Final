package com.spring.jwt.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Append-only audit trail for security, admin, and commerce events (complements {@code payment_audit_log}).
 */
@Data
@Entity
@Table(name = "application_audit_log", indexes = {
        @Index(name = "idx_app_audit_created", columnList = "created_at"),
        @Index(name = "idx_app_audit_category_action", columnList = "category,action_type"),
        @Index(name = "idx_app_audit_actor", columnList = "actor_user_id")
})
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApplicationAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "category", nullable = false, length = 30)
    private String category;

    @Column(name = "action_type", nullable = false, length = 64)
    private String actionType;

    @Column(name = "outcome", nullable = false, length = 16)
    private String outcome;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_label", length = 255)
    private String actorLabel;

    @Column(name = "resource_type", length = 64)
    private String resourceType;

    @Column(name = "resource_id", length = 128)
    private String resourceId;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
