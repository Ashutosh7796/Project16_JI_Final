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
        if (isBlank(config.getWorkingKey())) { log.error("CCAvenue workingKey is EMPTY"); valid = false; }
        if (isBlank(config.getAccessCode()))  { log.error("CCAvenue accessCode is EMPTY"); valid = false; }
        if (isBlank(config.getMerchantId()))   { log.error("CCAvenue merchantId is EMPTY"); valid = false; }
        if (isBlank(config.getPaymentUrl()))   { log.error("CCAvenue paymentUrl is EMPTY"); valid = false; }
        if (isBlank(config.getRedirectUrl()))  { log.warn("CCAvenue redirectUrl is EMPTY");  }
        if (isBlank(config.getCancelUrl()))    { log.warn("CCAvenue cancelUrl is EMPTY");    }

        if (valid) {
            log.info("CCAvenue config loaded: merchantId={}, accessCode={}..., paymentUrl={}, redirectUrl={}, cancelUrl={}",
                    config.getMerchantId(),
                    config.getAccessCode().substring(0, Math.min(4, config.getAccessCode().length())) + "****",
                    config.getPaymentUrl(),
                    config.getRedirectUrl(),
                    config.getCancelUrl());
        }
    }

    public String generatePaymentForm(CcAvenuePaymentRequest request) {
        String amount = AMOUNT_FORMAT.format(request.getAmount());

        String redirectUrl = request.getRedirectUrl() != null ? request.getRedirectUrl() : config.getRedirectUrl();
        String cancelUrl = request.getCancelUrl() != null ? request.getCancelUrl() : config.getCancelUrl();

        // CCAvenue expects plain-text key=value pairs separated by &
        // Values must NOT be URL-encoded -- the entire string gets AES-encrypted
        StringBuilder data = new StringBuilder();
        data.append("merchant_id=").append(config.getMerchantId())
                .append("&order_id=").append(sanitizeParam(request.getOrderId()))
                .append("&currency=").append(request.getCurrency() != null ? request.getCurrency() : "INR")
                .append("&amount=").append(amount)
                .append("&redirect_url=").append(nullSafe(redirectUrl))
                .append("&cancel_url=").append(nullSafe(cancelUrl))
                .append("&language=EN")
                .append("&billing_name=").append(nullSafe(request.getBillingName()))
                .append("&billing_address=").append(nullSafe(request.getBillingAddress()))
                .append("&billing_tel=").append(nullSafe(request.getBillingTel()));

        if (request.getBillingEmail() != null && !request.getBillingEmail().isBlank()) {
            data.append("&billing_email=").append(request.getBillingEmail());
        }

        log.debug("CCAvenue request data (pre-encrypt): merchant_id={}, order_id={}, amount={}, redirect_url={}",
                config.getMerchantId(), request.getOrderId(), amount, redirectUrl);

        String encrypted = CcAvenueUtil.encrypt(data.toString(), config.getWorkingKey());

        return "<html><body onload='document.forms[0].submit()'>"
                + "<form method='post' action='" + config.getPaymentUrl() + "'>"
                + "<input type='hidden' name='encRequest' value='" + encrypted + "'/>"
                + "<input type='hidden' name='access_code' value='" + config.getAccessCode() + "'/>"
                + "</form></body></html>";
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }

    private String sanitizeParam(String value) {
        if (value == null) return "";
        return value.replaceAll("[^a-zA-Z0-9_\\-]", "");
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
