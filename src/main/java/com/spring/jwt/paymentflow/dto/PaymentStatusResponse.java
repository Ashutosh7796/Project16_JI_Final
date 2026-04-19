package com.spring.jwt.paymentflow.dto;

import com.spring.jwt.paymentflow.PaymentFlowStatus;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;

@Value
@Builder
public class PaymentStatusResponse {
    Long paymentId;
    PaymentFlowStatus status;
    BigDecimal amount;
    String currency;
    String orderRef;
    String gatewayTransactionId;
    String errorCode;
    String errorMessage;
    Instant abandonedAt;
    Instant pendingGatewaySince;
    Instant updatedAt;
}
