package com.spring.jwt.checkout;

import java.util.concurrent.ThreadLocalRandom;

public final class CheckoutMerchantOrderIds {

    private CheckoutMerchantOrderIds() {
    }

    public static String forOrderId(long orderId) {
        // Amazon-style formatting: Base prefix increments by 1 for every 10 Million
        // orders
        long prefix = 402 + (orderId / 10_000_000L);

        // Random 7-digit segment entirely conceals exact predictability
        long randomPart = ThreadLocalRandom.current().nextLong(1000000L, 9999999L);

        // Final 7 digits represent the explicit unique sequence inside the current
        // prefix space
        long sequencePart = orderId % 10_000_000L;

        return String.format("%03d-%07d-%07d", prefix, randomPart, sequencePart);
    }
}
