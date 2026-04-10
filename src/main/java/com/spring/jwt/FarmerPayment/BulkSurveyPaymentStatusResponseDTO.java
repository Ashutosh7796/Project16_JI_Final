package com.spring.jwt.FarmerPayment;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class BulkSurveyPaymentStatusResponseDTO {
    private Map<String, FarmerPaymentResponseDTO> successfulPaymentsBySurveyId;
}
