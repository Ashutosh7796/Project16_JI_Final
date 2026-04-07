package com.spring.jwt.entity;

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
    private Long ProductBuyPendingId;

    private Long userId;

    private Long productId;

    private Integer quantity;

    private Double totalAmount;

    private String paymentStatus; // PENDING, FAILED, SUCCESS

    private String paymentGatewayOrderId;

    private LocalDateTime createdAt = LocalDateTime.now();

    private String deliveryAddress;

    private String customerName;

    private String contactNumber;
}