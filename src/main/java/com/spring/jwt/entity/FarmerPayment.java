package com.spring.jwt.entity;

import com.spring.jwt.Enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "farmer_payment", indexes = {
        @Index(name = "idx_farmer_payment_survey", columnList = "survey_id"),
        @Index(name = "idx_farmer_payment_user", columnList = "user_id"),
        @Index(name = "idx_farmer_payment_tracking", columnList = "tracking_id"),
        @Index(name = "idx_farmer_payment_idempotency", columnList = "idempotency_key"),
        @Index(name = "idx_farmer_payment_order", columnList = "ccavenue_order_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_farmer_payment_idempotency", columnNames = "idempotency_key"),
        @UniqueConstraint(name = "uk_farmer_payment_order", columnNames = "ccavenue_order_id")
})
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FarmerPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "payment_public_id", unique = true, updatable = false, length = 40)
    private String paymentPublicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "survey_id", nullable = false)
    private EmployeeFarmerSurvey survey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "user_id", nullable = false)
    private User user;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus;

    @Column(name = "tracking_id", length = 100)
    private String trackingId;

    @Column(name = "bank_ref_no", length = 100)
    private String bankRefNo;

    @Column(name = "ccavenue_payment_mode", length = 50)
    private String ccavenuePaymentMode;

    @Column(name = "ccavenue_order_id", nullable = false, length = 100)
    private String ccavenueOrderId;

    @Column(name = "status_message", length = 500)
    private String statusMessage;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Version
    private Long version;

    @Column(name = "initiator_ip", length = 45)
    private String initiatorIp;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "attempt_count")
    private Integer attemptCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    private void assignPublicIdIfMissing() {
        if (this.paymentPublicId == null || this.paymentPublicId.isBlank()) {
            this.paymentPublicId = "pay_" + UUID.randomUUID().toString().replace("-", "");
        }
    }
}
