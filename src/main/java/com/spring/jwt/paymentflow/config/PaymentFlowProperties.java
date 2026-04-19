package com.spring.jwt.paymentflow.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunables for reconciliation and webhook verification. Override in env-specific properties.
 */
@Data
@ConfigurationProperties(prefix = "app.payment-flow")
public class PaymentFlowProperties {

    /** HMAC secret for {@code POST /api/v1/payment-engine/webhook} (hex signature header). */
    private String webhookSecret = "change-me-in-production";

    /** Mark {@link com.spring.jwt.paymentflow.PaymentFlowStatus#PENDING_GATEWAY} stale after this many minutes. */
    private int pendingGatewayStaleMinutes = 3;

    /** After abandon, auto-fail if still not SUCCESS after this many minutes. */
    private int abandonedFailAfterMinutes = 1;

    /** How often the reconciliation job runs (ms). */
    private long reconcileFixedDelayMs = 60_000L;
}
