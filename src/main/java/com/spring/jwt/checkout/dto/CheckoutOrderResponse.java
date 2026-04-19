package com.spring.jwt.checkout.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class CheckoutOrderResponse {
    private Long orderId;
    private String status;
    private String merchantOrderId;
    private BigDecimal totalAmount;
    private String currency;
    private LocalDateTime reservationExpiresAt;
    /** When the checkout order row was created (for order history). */
    private LocalDateTime createdAt;
    private List<CheckoutOrderLineResponse> lines;
    /** When the order was paid (null if not yet paid). */
    private LocalDateTime paidAt;
    /** Present when order reached a terminal failure (customer-visible). */
    private String failReason;
    /** Latest refund workflow status for this order, if any. */
    private String refundStatus;
    
    // Customer Details
    private String customerName;
    private String contactNumber;
    private String deliveryAddress;
}
