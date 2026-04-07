package com.spring.jwt.entity;

import com.spring.jwt.Enums.PaymentMode;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "product_buy_confirmed")
@AllArgsConstructor
@NoArgsConstructor
public class ProductBuyConfirmed {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private Long productId;

    @ManyToOne
    @JoinColumn(name = "productId", insertable = false, updatable = false)
    private Product product;

    private Integer quantity;

    private Double totalAmount;

    private String paymentId;

    @Enumerated(EnumType.STRING)
    private PaymentMode paymentMode;

    private LocalDateTime paymentDate;

    private LocalDateTime createdAt = LocalDateTime.now();

    // Delivery Info
    private String deliveryAddress;
    private String customerName;
    private String contactNumber;

    // 🔥 Important for tracking system
    private Boolean deliveryCreated = false;
}