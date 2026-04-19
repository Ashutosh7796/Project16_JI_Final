package com.spring.jwt.paymentflow.dto;

import com.spring.jwt.paymentflow.PaymentFlowStatus;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class CreatePaymentResponse {
    Long paymentId;
    PaymentFlowStatus status;
    String abandonSecret;
    BigDecimal amount;
    String currency;
    String orderRef;
}
