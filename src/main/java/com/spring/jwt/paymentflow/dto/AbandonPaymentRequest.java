package com.spring.jwt.paymentflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AbandonPaymentRequest {
    @NotNull
    private Long paymentId;

    /** Opaque secret returned only once from {@code create} — allows unauthenticated {@code sendBeacon}. */
    @NotBlank
    private String abandonSecret;
}
