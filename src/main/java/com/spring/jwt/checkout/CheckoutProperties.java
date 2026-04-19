package com.spring.jwt.checkout;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.checkout")
public class CheckoutProperties {

    /**
     * How long stock stays reserved after payment initiation.
     */
    private int reservationTtlMinutes = 3;

    /**
     * Orders in PAYMENT_PENDING older than this are polled against CCAvenue status API.
     */
    private int reconcileOrderAgeMinutes = 3;

    private boolean reconciliationEnabled = true;

    /**
     * Optional override for CCAvenue redirect_url on checkout payments (must accept POST from CCAvenue).
     * When blank, falls back to {@code ccavenue.redirect-url}.
     */
    private String ccavenueRedirectUrl = "";

    /**
     * Optional override for CCAvenue cancel_url on checkout payments.
     */
    private String ccavenueCancelUrl = "";

    /**
     * Automated refund reconciliation (order-status polling).
     */
    private RefundSettings refund = new RefundSettings();

    @Data
    public static class RefundSettings {
        private int maxAutoRetries = 15;
        private int baseBackoffMinutes = 2;
        private int maxBackoffMinutes = 120;
    }
}
