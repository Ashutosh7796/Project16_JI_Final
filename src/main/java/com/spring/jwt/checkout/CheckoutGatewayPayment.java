package com.spring.jwt.checkout;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "checkout_gateway_payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutGatewayPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private CheckoutOrder order;

    @Column(name = "tracking_id", nullable = false, length = 120, unique = true)
    private String trackingId;

    @Column(name = "gateway_order_id", length = 120)
    private String gatewayOrderId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CheckoutGatewayPaymentRecordStatus status;

    @Column(name = "payment_mode", length = 50)
    private String paymentMode;

    @Column(name = "status_message", length = 500)
    private String statusMessage;

    @Column(name = "raw_response_enc", columnDefinition = "TEXT")
    private String rawResponseEnc;

    @Column(name = "raw_response_dec", columnDefinition = "TEXT")
    private String rawResponseDec;

    @Column(name = "client_ip", length = 45)
    private String clientIp;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
