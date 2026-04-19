package com.spring.jwt.checkout;

public class PaymentErrorClassifier {

    /**
     * Determines a strongly typed FailureReason based on the raw status message from a gateway.
     */
    public static FailureReason classify(String statusMessage) {
        if (statusMessage == null || statusMessage.isBlank()) {
            return FailureReason.UNKNOWN;
        }
        
        String lower = statusMessage.toLowerCase();
        
        if (lower.contains("merchant authentication failed") || 
            lower.contains("invalid api key") || 
            lower.contains("unauthorized") ||
            lower.contains("authentication failed")) {
            return FailureReason.AUTH_FAILED;
        }
        
        if (lower.contains("timeout") || 
            lower.contains("connection refused") ||
            lower.contains("read timed out") ||
            lower.contains("gateway timeout")) {
            return FailureReason.TIMEOUT;
        }
        
        if (lower.contains("invalid request") || 
            lower.contains("bad request") || 
            lower.contains("validation failed")) {
            return FailureReason.INVALID_REQUEST;
        }
        
        if (lower.contains("signature mismatch") || 
            lower.contains("checksum failed") ||
            lower.contains("hash mismatch")) {
            return FailureReason.SIGNATURE_MISMATCH;
        }
        
        return FailureReason.UNKNOWN;
    }
}
