package com.spring.jwt.FarmerPayment;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FarmerPaymentInitiateDTO {

    @NotBlank(message = "Survey ID is required")
    private String surveyId;
}
