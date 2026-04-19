package com.spring.jwt.paymentflow;

/**
 * Mandatory lifecycle states. Names are persisted as strings (MySQL VARCHAR).
 */
public enum PaymentFlowStatus {
    INITIATED,
    PROCESSING,
    PENDING_GATEWAY,
    SUCCESS,
    FAILED,
    ABANDONED
}
