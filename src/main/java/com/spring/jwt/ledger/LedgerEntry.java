package com.spring.jwt.ledger;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Append-only financial ledger entry.
 * <p>
 * DESIGN RULES:
 * <ul>
 *   <li>NO updates — every row is immutable once persisted</li>
 *   <li>NO deletes — soft or hard</li>
 *   <li>Idempotency enforced via unique {@code idempotencyKey}</li>
 *   <li>Positive amount = money IN (payment), negative = money OUT (refund)</li>
 * </ul>
 */
@Entity
@Table(name = "ledger_entries", indexes = {
        @Index(name = "idx_ledger_order_id", columnList = "order_id"),
        @Index(name = "idx_ledger_type", columnList = "type"),
        @Index(name = "idx_ledger_status", columnList = "status"),
        @Index(name = "idx_ledger_created_at", columnList = "created_at"),
        @Index(name = "idx_ledger_idempotency", columnList = "idempotency_key", unique = true)
})
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "refund_id")
    private Long refundId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LedgerEntryType type;

    /** Positive = inflow (payment captured), Negative = outflow (refund issued). */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LedgerEntryStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LedgerSource source;

    @Column(name = "gateway_tracking_id", length = 120)
    private String gatewayTrackingId;

    /** Unique key to prevent duplicate entries. Format: TYPE-orderId-trackingId or TYPE-orderId-refundId */
    @Column(name = "idempotency_key", nullable = false, length = 128, unique = true)
    private String idempotencyKey;

    /** JSON metadata for extensibility (e.g. raw callback params, admin notes). */
    @Column(columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    // NO @PreUpdate — entity is immutable after persist
    // NO setters — only builder for creation
}
