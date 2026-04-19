package com.spring.jwt.paymentflow.webhook;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * HMAC-SHA256 over raw body — compare in near-constant time. Webhook is trusted only after verification.
 */
public final class PaymentFlowWebhookSignatureUtil {

    private PaymentFlowWebhookSignatureUtil() {
    }

    public static String hmacSha256Hex(String secret, String rawBody) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] out = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC failed", e);
        }
    }

    public static boolean secureEqualsHex(String expectedHex, String actualHex) {
        if (expectedHex == null || actualHex == null) {
            return false;
        }
        String a = expectedHex.toLowerCase();
        String b = actualHex.toLowerCase();
        if (a.length() != b.length()) {
            return false;
        }
        int acc = 0;
        for (int i = 0; i < a.length(); i++) {
            acc |= a.charAt(i) ^ b.charAt(i);
        }
        return acc == 0;
    }
}
