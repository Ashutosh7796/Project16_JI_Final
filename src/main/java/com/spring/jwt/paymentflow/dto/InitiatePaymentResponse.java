package com.spring.jwt.paymentflow.dto;

import com.spring.jwt.paymentflow.PaymentFlowStatus;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class InitiatePaymentResponse {
    Long paymentId;
    PaymentFlowStatus status;
    /** JSON string: may include {@code paymentFormHtml} for CCAvenue, or Razorpay order fields, etc. */
    String gatewayClientPayloadJson;
    String errorCode;
    String errorMessage;
}
