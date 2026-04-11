package com.spring.jwt.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "payment_audit_log", indexes = {
        @Index(name = "idx_pay_audit_payment", columnList = "payment_id"),
        @Index(name = "idx_pay_audit_action", columnList = "action_type"),
        @Index(name = "idx_pay_audit_time", columnList = "action_at"),
        @Index(name = "idx_pay_audit_order", columnList = "ccavenue_order_id")
})
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_type", nullable = false, length = 20)
    private String paymentType;

    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "ccavenue_order_id", length = 100)
    private String ccavenueOrderId;

    @Column(name = "action_type", nullable = false, length = 30)
    private String actionType;

    @Column(name = "old_status", length = 20)
    private String oldStatus;

    @Column(name = "new_status", length = 20)
    private String newStatus;

    @Column(name = "action_by_user_id")
    private Long actionByUserId;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "action_at", nullable = false)
    private LocalDateTime actionAt;

    @PrePersist
    protected void onCreate() {
        if (actionAt == null) {
            actionAt = LocalDateTime.now();
        }
    }
}
