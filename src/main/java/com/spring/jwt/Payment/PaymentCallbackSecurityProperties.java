package com.spring.jwt.Payment;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Operational guardrails for the public payment callback endpoints. All defaults are permissive
 * so this can be enabled incrementally per environment via {@code app.payment.callback.*}.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.payment.callback")
public class PaymentCallbackSecurityProperties {

    /**
     * Comma-separated CIDRs that may POST to the gateway callback endpoints. Empty means
     * "no allow-list enforced" (preserves the current behavior so this can be rolled out safely).
     * Set in production to {@code 122.171.0.0/16,...} as published by CCAvenue ops.
     */
    private List<String> allowedCidrs = Collections.emptyList();

    /**
     * Maximum callback POSTs accepted per source IP per {@link #rateWindowSeconds}. {@code 0}
     * disables the limiter. Sized for one user retry-storming the cancel page, not for the
     * gateway itself (gateway IPs should ideally be on the allow-list and bypass this).
     */
    private int rateMaxPerWindow = 60;

    private int rateWindowSeconds = 60;

    /**
     * CIDRs (or exact IPs) that we trust as reverse proxies / load balancers. {@code X-Forwarded-For}
     * is honoured ONLY when the immediate {@code RemoteAddr} matches one of these. Empty means
     * "trust nothing" — falls back to {@code RemoteAddr} every time.
     */
    private List<String> trustedProxies = Collections.emptyList();
}
