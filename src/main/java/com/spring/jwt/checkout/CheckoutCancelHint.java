package com.spring.jwt.checkout;

import com.spring.jwt.paymentflow.webhook.PaymentFlowWebhookSignatureUtil;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Tamper-evident hint appended to the CCAvenue cancel URL as {@code ?cmref=...}.
 *
 * <p>Format: {@code merchantOrderId.expirySec.hmacHex}. Hex HMAC is over the same
 * "{@code merchantOrderId|expirySec}" string with the configured cancel-hint secret. Comparison
 * uses {@link PaymentFlowWebhookSignatureUtil#secureEqualsHex(String, String)} for constant-time
 * matching.</p>
 *
 * <p>This intentionally does NOT carry the userId; the merchantOrderId already uniquely
 * identifies a single checkout order, and the HMAC prevents any cross-user spoofing.</p>
 */
public final class CheckoutCancelHint {

    private CheckoutCancelHint() {
    }

    private static final char SEPARATOR = '.';
    private static final long DEFAULT_TTL_SECONDS = 30L * 60L;

    public static String issue(String merchantOrderId, String secret) {
        return issue(merchantOrderId, secret, DEFAULT_TTL_SECONDS);
    }

    public static String issue(String merchantOrderId, String secret, long ttlSeconds) {
        if (!StringUtils.hasText(merchantOrderId)) {
            throw new IllegalArgumentException("merchantOrderId required");
        }
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("checkout cancel-hint secret is not configured");
        }
        long expiry = Instant.now().getEpochSecond() + Math.max(60L, ttlSeconds);
        String safeId = urlEncodeBase64(merchantOrderId);
        String payload = safeId + SEPARATOR + expiry;
        String mac = PaymentFlowWebhookSignatureUtil.hmacSha256Hex(secret, payload);
        return payload + SEPARATOR + mac;
    }

    /**
     * Returns the original merchantOrderId only if the hint is well-formed, in date, and the
     * HMAC matches. Any malformed / expired / tampered token is silently rejected.
     */
    public static Optional<String> verify(String token, String secret) {
        if (!StringUtils.hasText(token) || !StringUtils.hasText(secret)) {
            return Optional.empty();
        }
        String trimmed = token.trim();
        int firstSep = trimmed.indexOf(SEPARATOR);
        int secondSep = trimmed.indexOf(SEPARATOR, firstSep + 1);
        if (firstSep <= 0 || secondSep <= firstSep + 1 || secondSep == trimmed.length() - 1) {
            return Optional.empty();
        }
        String payload = trimmed.substring(0, secondSep);
        String mac = trimmed.substring(secondSep + 1);
        String expectedMac = PaymentFlowWebhookSignatureUtil.hmacSha256Hex(secret, payload);
        if (!PaymentFlowWebhookSignatureUtil.secureEqualsHex(expectedMac, mac)) {
            return Optional.empty();
        }
        long expiry;
        try {
            expiry = Long.parseLong(trimmed.substring(firstSep + 1, secondSep));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        if (Instant.now().getEpochSecond() > expiry) {
            return Optional.empty();
        }
        try {
            String safeId = trimmed.substring(0, firstSep);
            byte[] decoded = Base64.getUrlDecoder().decode(safeId);
            String merchantOrderId = new String(decoded, StandardCharsets.UTF_8);
            if (!StringUtils.hasText(merchantOrderId)) {
                return Optional.empty();
            }
            return Optional.of(merchantOrderId);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String urlEncodeBase64(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
