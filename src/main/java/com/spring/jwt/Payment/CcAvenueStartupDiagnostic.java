package com.spring.jwt.Payment;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * One-time startup log of which CCAvenue properties are actually bound.
 * Remove this class after debugging credential/profile issues.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CcAvenueStartupDiagnostic {

    private final CcAvenueConfig ccAvenueConfig;
    private final Environment environment;

    @PostConstruct
    public void logMaskedCcAvenueConfig() {
        String[] profiles = environment.getActiveProfiles();
        String profileInfo = profiles.length > 0 ? String.join(",", profiles) : "(default)";

        log.info("=== CCAvenue config snapshot (masked) | active profiles: {} ===", profileInfo);
        log.info("  merchantId   : {}", maskMiddle(ccAvenueConfig.getMerchantId()));
        log.info("  accessCode   : {} (len={})", maskSecret(ccAvenueConfig.getAccessCode()), lengthOf(ccAvenueConfig.getAccessCode()));
        log.info("  workingKey   : {} (len={})", maskSecret(ccAvenueConfig.getWorkingKey()), lengthOf(ccAvenueConfig.getWorkingKey()));
        log.info("  paymentUrl   : {}", nullToEmpty(ccAvenueConfig.getPaymentUrl()));
        log.info("  redirectUrl  : {}", nullToEmpty(ccAvenueConfig.getRedirectUrl()));
        log.info("  cancelUrl    : {}", nullToEmpty(ccAvenueConfig.getCancelUrl()));
        log.info("  statusApiUrl : {}", nullToEmpty(ccAvenueConfig.getStatusApiUrl()));
        log.info("=== end CCAvenue snapshot ===");

        if (isBlank(ccAvenueConfig.getWorkingKey()) || isBlank(ccAvenueConfig.getAccessCode())) {
            log.warn("CCAvenue: workingKey or accessCode is blank — gateway will reject requests (e.g. 10002).");
        }
    }

    private static int lengthOf(String s) {
        return s == null ? 0 : s.length();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "<null>" : s;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** Show first/last few chars only; never log full secrets. */
    private static String maskSecret(String value) {
        if (value == null) {
            return "<null>";
        }
        String v = value.trim();
        if (v.isEmpty()) {
            return "<empty>";
        }
        if (v.length() <= 4) {
            return "**** (len=" + v.length() + ")";
        }
        return v.substring(0, 2) + "…" + v.substring(v.length() - 2) + " (len=" + v.length() + ")";
    }

    private static String maskMiddle(String value) {
        if (value == null) {
            return "<null>";
        }
        String v = value.trim();
        if (v.isEmpty()) {
            return "<empty>";
        }
        if (v.length() <= 8) {
            return v.charAt(0) + "…" + v.charAt(v.length() - 1) + " (len=" + v.length() + ")";
        }
        return v.substring(0, 4) + "…" + v.substring(v.length() - 4) + " (len=" + v.length() + ")";
    }
}
