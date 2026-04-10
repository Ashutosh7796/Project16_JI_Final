package com.spring.jwt.Payment;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentCallbackQueueStatusDTO {
    private Long queueId;
    private String paymentType;
    private String callbackType;
    private String status;
    private Integer attemptCount;
    private String resultStatus;
    private String orderId;
    private String message;
}

