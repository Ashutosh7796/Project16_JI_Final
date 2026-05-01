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
        StringBuilder errors = new StringBuilder();
        String workingKey = trimToEmpty(config.getWorkingKey());
        String accessCode = trimToEmpty(config.getAccessCode());
        String merchantId = trimToEmpty(config.getMerchantId());

        if (isBlank(workingKey)) errors.append("CCAvenue workingKey is EMPTY. ");
        if (isBlank(accessCode)) errors.append("CCAvenue accessCode is EMPTY. ");
        if (isBlank(merchantId)) errors.append("CCAvenue merchantId is EMPTY. ");
        if (isBlank(config.getPaymentUrl())) errors.append("CCAvenue paymentUrl is EMPTY. ");
        if (isBlank(config.getRedirectUrl())) log.warn("CCAvenue redirectUrl is EMPTY — fallback may apply");
        if (isBlank(config.getCancelUrl())) log.warn("CCAvenue cancelUrl is EMPTY — fallback may apply");

        // workingKey must be exactly 32 hex chars for AES-128
        if (!isBlank(workingKey) && !workingKey.matches("^[A-Fa-f0-9]{32}$")) {
            errors.append(String.format("CCAvenue workingKey format invalid. Expected 32 hex characters, got length=%d. ", workingKey.length()));
        }

        if (errors.length() > 0) {
            log.error("CCAvenue configuration errors: {}", errors);
            throw new IllegalStateException("CCAvenue misconfigured — payment gateway will not work. Fix application properties. Errors: " + errors);
        }

        log.info("CCAvenue config loaded: merchantId={}, accessCode={}..., paymentUrl={}, redirectUrl={}, cancelUrl={}",
                merchantId,
                accessCode.substring(0, Math.min(4, accessCode.length())) + "****",
                config.getPaymentUrl(),
                config.getRedirectUrl(),
                config.getCancelUrl());
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

        String encrypted = CcAvenueUtil.encrypt(plainText, workingKey);
        log.debug("CCAvenue payment request prepared for orderId={}, amount={}",
                sanitizeOrderId(request.getOrderId()), amount);

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
