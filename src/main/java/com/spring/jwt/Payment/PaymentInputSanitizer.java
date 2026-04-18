package com.spring.jwt.Payment;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PaymentInputSanitizer {

    private PaymentInputSanitizer() {}

    public static String sanitizeName(String name) {
        if (name == null) return "";
        return name.replaceAll("[<>\"'&;(){}\\[\\]]", "").trim();
    }

    public static String sanitizePhone(String phone) {
        if (phone == null) return "";
        return phone.replaceAll("[^0-9+\\-() ]", "").trim();
    }

    public static String sanitizeAddress(String address) {
        if (address == null) return "";
        return address.replaceAll("[<>\"'&;(){}\\[\\]]", "").trim();
    }

    public static BigDecimal sanitizeAmount(BigDecimal amount, BigDecimal maxAllowed) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
        if (maxAllowed != null && amount.compareTo(maxAllowed) > 0) {
            throw new IllegalArgumentException("Payment amount exceeds maximum allowed: " + maxAllowed);
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static String sanitizeOrderId(String orderId) {
        if (orderId == null) return "";
        return orderId.replaceAll("[^a-zA-Z0-9_\\-]", "");
    }

    public static String sanitizeGeneral(String input) {
        if (input == null) return "";
        return input.replaceAll("[<>\"'&;]", "").trim();
    }

    /** Callback queue polling token: alphanumeric, hyphens, underscores only (max 64). */
    public static String sanitizeOpaqueToken(String token) {
        if (token == null) return "";
        String t = token.trim();
        if (t.length() > 64) {
            t = t.substring(0, 64);
        }
        return t.replaceAll("[^a-zA-Z0-9_-]", "");
    }
}
