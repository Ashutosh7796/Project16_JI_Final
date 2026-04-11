package com.spring.jwt.ProductBuyPending;


import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentVerifyDto {

    private Long pendingOrderId;

    private String paymentId;

    private String paymentMode; // CCAVENUE, UPI, CARD

    private String orderId;

    private String amount;

}