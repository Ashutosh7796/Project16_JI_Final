package com.spring.jwt.FarmerPayment;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class FarmerPaymentResponseDTO {

    private Long paymentId;
    private Long surveyId;
    private String farmerName;
    private Long userId;
    private BigDecimal amount;
    private String paymentStatus;
    private String ccavenueOrderId;
    private String trackingId;
    private String bankRefNo;
    private String ccavenuePaymentMode;
    private String statusMessage;
    private Integer attemptCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String paymentFormHtml;
}
