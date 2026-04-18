package com.spring.jwt.Payment;

/**
 * Result of enqueueing a gateway callback for async processing.
 *
 * @param queueId    Internal row id (operations / admin only).
 * @param statusToken Opaque token safe to expose to browsers for status polling.
 */
public record PaymentCallbackEnqueueResult(Long queueId, String statusToken) {}
