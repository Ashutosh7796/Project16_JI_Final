package com.spring.jwt.checkout.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InitiateCheckoutPaymentResponse {
    private Long orderId;
    private String merchantOrderId;
    private String paymentFormHtml;
    /** Random secret for unauthenticated beacon-based abandon (tab close). */
    private String abandonToken;
}
