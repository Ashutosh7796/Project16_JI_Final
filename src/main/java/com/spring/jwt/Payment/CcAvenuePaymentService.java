package com.spring.jwt.Payment;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;

@Service
@RequiredArgsConstructor
@Slf4j
public class CcAvenuePaymentService {

    private final CcAvenueConfig config;

    private static final DecimalFormat AMOUNT_FORMAT = new DecimalFormat("0.00");

    @PostConstruct
    void validateConfig() {
        boolean valid = true;
        String workingKey = trimToEmpty(config.getWorkingKey());
        String accessCode = trimToEmpty(config.getAccessCode());
        String merchantId = trimToEmpty(config.getMerchantId());

        if (isBlank(workingKey)) { log.error("CCAvenue workingKey is EMPTY"); valid = false; }
        if (isBlank(accessCode))  { log.error("CCAvenue accessCode is EMPTY"); valid = false; }
        if (isBlank(merchantId))   { log.error("CCAvenue merchantId is EMPTY"); valid = false; }
        if (isBlank(config.getPaymentUrl()))   { log.error("CCAvenue paymentUrl is EMPTY"); valid = false; }
        if (isBlank(config.getRedirectUrl()))  { log.warn("CCAvenue redirectUrl is EMPTY");  }
        if (isBlank(config.getCancelUrl()))    { log.warn("CCAvenue cancelUrl is EMPTY");    }
        if (!workingKey.matches("^[A-Fa-f0-9]{32}$")) {
            log.error("CCAvenue workingKey format invalid. Expected 32 hex characters, got length={}", workingKey.length());
            valid = false;
        }

        if (valid) {
            log.info("CCAvenue config loaded: merchantId={}, accessCode={}..., paymentUrl={}, redirectUrl={}, cancelUrl={}",
                    merchantId,
                    accessCode.substring(0, Math.min(4, accessCode.length())) + "****",
                    config.getPaymentUrl(),
                    config.getRedirectUrl(),
                    config.getCancelUrl());
        }
    }

    public String generatePaymentForm(CcAvenuePaymentRequest request) {
        String amount = AMOUNT_FORMAT.format(request.getAmount());

        String redirectUrl = request.getRedirectUrl() != null ? request.getRedirectUrl() : config.getRedirectUrl();
        String cancelUrl = request.getCancelUrl() != null ? request.getCancelUrl() : config.getCancelUrl();

        String merchantId = trimToEmpty(config.getMerchantId());
        String accessCode = trimToEmpty(config.getAccessCode());
        String workingKey = trimToEmpty(config.getWorkingKey());

        StringBuilder data = new StringBuilder();
        data.append("merchant_id=").append(merchantId)
                .append("&order_id=").append(sanitizeOrderId(request.getOrderId()))
                .append("&currency=").append(request.getCurrency() != null ? request.getCurrency() : "INR")
                .append("&amount=").append(amount)
                .append("&redirect_url=").append(nullSafe(redirectUrl))
                .append("&cancel_url=").append(nullSafe(cancelUrl))
                .append("&language=EN")
                .append("&billing_name=").append(sanitizeText(request.getBillingName()))
                .append("&billing_address=").append(sanitizeText(request.getBillingAddress()))
                .append("&billing_tel=").append(sanitizePhone(request.getBillingTel()));

        if (request.getBillingEmail() != null && !request.getBillingEmail().isBlank()) {
            data.append("&billing_email=").append(sanitizeEmail(request.getBillingEmail()));
        }

        String plainText = data.toString();

        log.error("CCAvenue PLAIN TEXT request: {}", plainText);

        String encrypted = CcAvenueUtil.encrypt(plainText, workingKey);

        log.error("CCAvenue ENCRYPTED request (encRequest): {}", encrypted);
        log.error("CCAvenue access_code: {}", accessCode);
        log.error("CCAvenue payment URL: {}", config.getPaymentUrl());

        try {
            String decrypted = CcAvenueUtil.decrypt(encrypted, workingKey);
            log.error("CCAvenue DECRYPT VERIFICATION: {}", decrypted);
            if (!plainText.equals(decrypted)) {
                log.error("CCAvenue DECRYPT MISMATCH! Original length={}, Decrypted length={}", plainText.length(), decrypted.length());
            } else {
                log.error("CCAvenue DECRYPT MATCH OK - encryption round-trip verified");
            }
        } catch (Exception e) {
            log.error("CCAvenue DECRYPT VERIFICATION FAILED: {}", e.getMessage());
        }

        return "<html><body onload='document.forms[0].submit()'>"
                + "<form method='post' action='" + config.getPaymentUrl() + "'>"
                + "<input type='hidden' name='encRequest' value='" + encrypted + "'/>"
                + "<input type='hidden' name='access_code' value='" + accessCode + "'/>"
                + "</form></body></html>";
    }

    /**
     * Strips characters that break CCAvenue's key=value&key=value format.
     * Removes: & (param separator), = (kv separator), # and newlines/tabs.
     * Keeps: letters, digits, spaces, commas, periods, hyphens, slashes, parentheses, etc.
     */
    private String sanitizeText(String value) {
        if (value == null) return "";
        return value.replaceAll("[&=#\r\n\t]", "").trim();
    }

    /** Order ID: alphanumeric, hyphens, underscores, dots only */
    private String sanitizeOrderId(String value) {
        if (value == null) return "";
        return value.replaceAll("[^a-zA-Z0-9_.\\-]", "");
    }

    /** Phone: digits, plus, hyphens, spaces only */
    private String sanitizePhone(String value) {
        if (value == null) return "";
        return value.replaceAll("[^0-9+\\- ]", "").trim();
    }

    /** Email: standard email chars only */
    private String sanitizeEmail(String value) {
        if (value == null) return "";
        return value.replaceAll("[^a-zA-Z0-9@.+_\\-]", "").trim();
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String trimToEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}
