package com.spring.jwt.checkout.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class InitiateCheckoutPaymentResponse {
    private Long orderId;
    private String merchantOrderId;
    /** Legacy auto-submit HTML — kept for back-compat. New clients should use {@link #paymentForm}. */
    private String paymentFormHtml;
    /** P3.2: structured form fields so the SPA can build the {@code <form>} via DOM APIs (no innerHTML). */
    private PaymentForm paymentForm;
    /** Random secret for unauthenticated beacon-based abandon (tab close). */
    private String abandonToken;

    @Data
    @Builder
    public static class PaymentForm {
        private String actionUrl;
        private Map<String, String> fields;
    }
}
