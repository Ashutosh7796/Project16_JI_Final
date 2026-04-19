package com.spring.jwt.checkout.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefundMarkFailedRequest {

    @NotBlank
    private String notes;
}
