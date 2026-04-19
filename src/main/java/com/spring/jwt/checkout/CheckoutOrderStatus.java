package com.spring.jwt.checkout;

public enum CheckoutOrderStatus {
    PENDING,
    PAYMENT_PENDING,
    PAID,
    FAILED,
    CANCELLED,
    REFUNDED
}
