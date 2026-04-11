package com.spring.jwt.ProductBuyConfirmed;

import com.spring.jwt.Enums.PaymentMode;
import lombok.*;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductBuyConfirmedDto {

    private Long id;

    private Long userId;

    private Long productId;

    private String productName; // 🔥 useful for UI

    private Integer quantity;

    private Double totalAmount;

    private String paymentId;

    private PaymentMode paymentMode;

    private LocalDateTime paymentDate;

    private LocalDateTime createdAt;

    // Delivery Info
    private String deliveryAddress;
    private String customerName;
    private String contactNumber;

    private Boolean deliveryCreated;
}