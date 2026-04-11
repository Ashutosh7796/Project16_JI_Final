package com.spring.jwt.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "payment_callback_queue", indexes = {
        @Index(name = "idx_pay_cbq_status_next", columnList = "status,next_attempt_at"),
        @Index(name = "idx_pay_cbq_created", columnList = "created_at")
})
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentCallbackQueue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_type", nullable = false, length = 20)
    private String paymentType;

    @Column(name = "callback_type", nullable = false, length = 20)
    private String callbackType;

    @Column(name = "enc_resp", columnDefinition = "TEXT")
    private String encResp;

    @Column(name = "client_ip", length = 45)
    private String clientIp;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "order_id", length = 120)
    private String orderId;

    @Column(name = "result_status", length = 30)
    private String resultStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    @PrePersist
    private void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) this.createdAt = now;
        if (this.nextAttemptAt == null) this.nextAttemptAt = now;
        if (this.status == null || this.status.isBlank()) this.status = "PENDING";
        if (this.attemptCount == null) this.attemptCount = 0;
    }

    @PreUpdate
    private void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
