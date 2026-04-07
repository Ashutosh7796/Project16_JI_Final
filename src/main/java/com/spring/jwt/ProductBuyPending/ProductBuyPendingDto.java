package com.spring.jwt.ProductBuyPending;

import lombok.*;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductBuyPendingDto {

    private Long id;

    private Long userId;

    private Long productId;

    private Integer quantity;

    private Double totalAmount;

    private String paymentStatus;

    private String paymentGatewayOrderId;

    private LocalDateTime createdAt;

    // Delivery Info
    private String deliveryAddress;
    private String customerName;
    private String contactNumber;
}