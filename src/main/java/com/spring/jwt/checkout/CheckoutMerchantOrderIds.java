package com.spring.jwt.checkout;

import java.util.concurrent.ThreadLocalRandom;

public final class CheckoutMerchantOrderIds {

    private CheckoutMerchantOrderIds() {}

    public static String forOrderId(long orderId) {
        // Format: 402-9697424-0638732
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int part1 = r.nextInt(100, 999);
        long part2 = r.nextLong(1000000L, 9999999L);
        long part3 = r.nextLong(1000000L, 9999999L);
        return String.format("%03d-%07d-%07d", part1, part2, part3);
    }
}
