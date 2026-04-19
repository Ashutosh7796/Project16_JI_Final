package com.spring.jwt.checkout.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefundManualSuccessRequest {

    @NotBlank
    private String notes;
}
