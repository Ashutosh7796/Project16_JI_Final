package com.spring.jwt.FarmerPayment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class FarmerPaymentInitiateDTO {

    @NotNull(message = "Survey ID is required")
    @Positive(message = "Survey ID must be positive")
    private Long surveyId;
}
