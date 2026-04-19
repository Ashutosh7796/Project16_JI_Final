package com.spring.jwt.checkout;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Locale;

/**
 * Defensive interpretation of CCAvenue order-status JSON for refund reconciliation.
 */
public final class RefundOrderStatusInterpreter {

    public enum Hint {
        SUCCESS,
        FAILED,
        UNKNOWN
    }

    private RefundOrderStatusInterpreter() {}

    public static Hint infer(JsonNode root, String refundRefNo) {
        if (root == null || root.isNull()) {
            return Hint.UNKNOWN;
        }
        for (String key : new String[]{
                "refund_status", "Refund_Status", "refund_flag", "order_refund_status",
                "refund_status_message", "refund_state"
        }) {
            if (root.has(key) && !root.get(key).isNull()) {
                String v = root.get(key).asText("").toLowerCase(Locale.ROOT);
                if (containsAny(v, "success", "complete", "processed", "done", "credited", "y", "s")) {
                    return Hint.SUCCESS;
                }
                if (containsAny(v, "fail", "reject", "declin", "error", "invalid", "n", "f")) {
                    return Hint.FAILED;
                }
            }
        }
        String orderStatus = root.path("order_status").asText("").toLowerCase(Locale.ROOT);
        if (orderStatus.contains("refund") && (orderStatus.contains("success") || orderStatus.contains("complete"))) {
            return Hint.SUCCESS;
        }
        String blob = root.toString().toLowerCase(Locale.ROOT);
        if (refundRefNo != null && !refundRefNo.isBlank() && blob.contains(refundRefNo.toLowerCase(Locale.ROOT))) {
            if (blob.contains("refund") && containsAny(blob, "success", "complete", "processed")) {
                return Hint.SUCCESS;
            }
            if (blob.contains("refund") && containsAny(blob, "fail", "reject", "declin")) {
                return Hint.FAILED;
            }
        }
        return Hint.UNKNOWN;
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String n : needles) {
            if (haystack.contains(n)) {
                return true;
            }
        }
        return false;
    }
}
