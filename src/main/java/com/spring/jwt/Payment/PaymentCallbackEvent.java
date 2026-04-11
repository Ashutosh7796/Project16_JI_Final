package com.spring.jwt.Payment;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Event published when a payment callback is enqueued.
 * This enables event-driven processing instead of continuous polling.
 */
@Getter
public class PaymentCallbackEvent extends ApplicationEvent {
    
    private final Long queueId;
    private final String paymentType;
    private final String callbackType;
    
    public PaymentCallbackEvent(Object source, Long queueId, String paymentType, String callbackType) {
        super(source);
        this.queueId = queueId;
        this.paymentType = paymentType;
        this.callbackType = callbackType;
    }
}
