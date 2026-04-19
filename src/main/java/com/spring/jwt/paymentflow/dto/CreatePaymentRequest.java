package com.spring.jwt.paymentflow.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreatePaymentRequest {

    @NotNull
    @DecimalMin(value = "0.01", inclusive = true, message = "amount must be positive")
    private BigDecimal amount;

    @Size(max = 3)
    private String currency = "INR";

    @NotBlank
    @Size(max = 128)
    private String orderRef;

    @NotBlank
    @Size(max = 200)
    private String billingName;

    @NotBlank
    @Size(max = 2000)
    private String billingAddress;

    @NotBlank
    @Size(max = 50)
    private String billingTel;
}
