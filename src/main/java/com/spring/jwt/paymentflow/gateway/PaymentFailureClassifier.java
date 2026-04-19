package com.spring.jwt.paymentflow.gateway;

import java.util.Locale;
import java.util.concurrent.TimeoutException;

/**
 * Classifies infrastructure vs deterministic merchant/config errors. Conservative: unknown → uncertain.
 */
public final class PaymentFailureClassifier {

    private PaymentFailureClassifier() {
    }

    public static boolean isDeterministic(Throwable t) {
        if (t == null) {
            return false;
        }
        if (t instanceof TimeoutException) {
            return false;
        }
        if (t instanceof IllegalArgumentException) {
            return true;
        }
        String msg = String.valueOf(t.getMessage()).toLowerCase(Locale.ROOT);
        if (msg.isBlank()) {
            return false;
        }
        return msg.contains("merchant authentication")
                || msg.contains("authentication failed")
                || msg.contains("invalid merchant")
                || msg.contains("signature")
                || msg.contains("bad request")
                || msg.contains("access denied")
                || msg.contains("10002")
                || msg.contains("invalid access")
                || msg.contains("checksum");
    }

    public static boolean isLikelyNetworkOrUnknown(Throwable t) {
        if (t instanceof TimeoutException) {
            return true;
        }
        String cn = t.getClass().getName();
        return cn.contains("SocketTimeout") || cn.contains("ConnectException") || cn.contains("UnknownHost");
    }
}
