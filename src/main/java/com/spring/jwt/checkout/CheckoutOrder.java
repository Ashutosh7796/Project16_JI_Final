package com.spring.jwt.checkout;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "checkout_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CheckoutOrderStatus status;

    @Column(name = "merchant_order_id", nullable = false, length = 64, unique = true)
    private String merchantOrderId;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "INR";

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "pricing_snapshot_hash", length = 128)
    private String pricingSnapshotHash;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "contact_number", nullable = false, length = 50)
    private String contactNumber;

    @Column(name = "delivery_address", nullable = false, columnDefinition = "TEXT")
    private String deliveryAddress;

    @Column(name = "checkout_idempotency_key", length = 64)
    private String checkoutIdempotencyKey;

    @Column(name = "payment_init_idempotency_key", length = 64)
    private String paymentInitIdempotencyKey;

    @Column(name = "reservation_expires_at")
    private LocalDateTime reservationExpiresAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "fail_reason", length = 500)
    private String failReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 30)
    private FailureReason failureReason;

    @Column(name = "refund_total_amount", precision = 14, scale = 2)
    private BigDecimal refundTotalAmount;

    @Column(name = "refund_note", length = 500)
    private String refundNote;

    /** Random secret for unauthenticated beacon-based abandon (tab close / navigation away). */
    @Column(name = "abandon_token", length = 64)
    private String abandonToken;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CheckoutOrderLine> lines = new ArrayList<>();

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void addLine(CheckoutOrderLine line) {
        lines.add(line);
        line.setOrder(this);
    }
}
