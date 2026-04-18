package com.spring.jwt.Payment;

import com.spring.jwt.exception.BaseException;

public class PaymentException extends BaseException {

    public PaymentException(String code, String message) {
        super(code, message);
    }

    public static PaymentException duplicatePayment(String orderId) {
        return new PaymentException("PAYMENT_DUPLICATE", "Payment already completed for order: " + orderId);
    }

    public static PaymentException fraudDetected(String reason) {
        return new PaymentException("PAYMENT_FRAUD", "Payment blocked: " + reason);
    }

    public static PaymentException invalidCallback(String reason) {
        return new PaymentException("PAYMENT_CALLBACK_INVALID", "Invalid payment callback: " + reason);
    }

    public static PaymentException rateLimitExceeded() {
        return new PaymentException("PAYMENT_RATE_LIMIT", "Too many payment attempts. Please try again later.");
    }

    public static PaymentException amountMismatch() {
        return new PaymentException("PAYMENT_AMOUNT_MISMATCH", "Payment amount does not match expected amount");
    }

    public static PaymentException paymentNotFound(String identifier) {
        return new PaymentException("PAYMENT_NOT_FOUND", "Payment not found: " + identifier);
    }

    public static PaymentException idempotencySurveyMismatch() {
        return new PaymentException("PAYMENT_IDEMPOTENCY_CONFLICT",
                "Idempotency key is already associated with a different survey");
    }
}
