package com.spring.jwt.entity;

import com.spring.jwt.Enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "product_buy_pending")
@AllArgsConstructor
@NoArgsConstructor
public class ProductBuyPending {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long productBuyPendingId;

    private Long userId;

    private Long productId;

    private Integer quantity;

    private Double totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PaymentStatus paymentStatus;

    private String paymentGatewayOrderId;

    private LocalDateTime createdAt = LocalDateTime.now();

    private String deliveryAddress;

    private String customerName;

    private String contactNumber;

    @Version
    private Long version;
}
