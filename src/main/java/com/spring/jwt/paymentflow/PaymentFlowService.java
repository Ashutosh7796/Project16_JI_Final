package com.spring.jwt.paymentflow;

import com.spring.jwt.paymentflow.dto.*;

public interface PaymentFlowService {

    CreatePaymentResponse create(Long userId, CreatePaymentRequest request, String idempotencyKey);

    InitiatePaymentResponse initiate(Long userId, InitiatePaymentRequest request, String idempotencyKey);

    void abandon(AbandonPaymentRequest request);

    void handleWebhook(String rawBody, String signatureHexHeader);

    PaymentStatusResponse status(Long userId, Long paymentId);

    /** Scheduled reconciliation: stale {@code PENDING_GATEWAY}/{@code PROCESSING} and expired {@code ABANDONED}. */
    void reconcileStalePayments();
}
