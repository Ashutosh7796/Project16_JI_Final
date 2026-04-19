package com.spring.jwt.paymentflow.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InitiatePaymentRequest {
    @NotNull
    private Long paymentId;
}
