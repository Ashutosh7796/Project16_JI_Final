package com.spring.jwt.checkout;

public enum FailureReason {
    AUTH_FAILED,
    INVALID_REQUEST,
    SIGNATURE_MISMATCH,
    NETWORK_ERROR,
    TIMEOUT,
    UNKNOWN
}
