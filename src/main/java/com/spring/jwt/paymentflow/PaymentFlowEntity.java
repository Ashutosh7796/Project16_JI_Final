package com.spring.jwt.paymentflow;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payment_flow_payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentFlowEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_ref", nullable = false, length = 128)
    private String orderRef;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentFlowStatus status;

    @Column(name = "gateway_kind", nullable = false, length = 32)
    @Builder.Default
    private String gatewayKind = "CCAVENUE_FORM";

    @Column(name = "gateway_transaction_id", length = 128)
    private String gatewayTransactionId;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "initiate_idempotency_key", length = 128)
    private String initiateIdempotencyKey;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "gateway_client_payload_json", columnDefinition = "TEXT")
    private String gatewayClientPayloadJson;

    @Column(name = "abandon_secret", nullable = false, length = 64)
    private String abandonSecret;

    @Column(name = "abandoned_at")
    private Instant abandonedAt;

    @Column(name = "pending_gateway_since")
    private Instant pendingGatewaySince;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    private Long version;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
