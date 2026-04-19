package com.spring.jwt.checkout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CheckoutMerchantOrderIdsTest {

    @Test
    void roundTripOrderId() {
        assertEquals("CHK42", CheckoutMerchantOrderIds.forOrderId(42L));
        assertEquals(42L, CheckoutMerchantOrderIds.parseOrderId("CHK42"));
    }

    @Test
    void rejectsInvalid() {
        assertNull(CheckoutMerchantOrderIds.parseOrderId(null));
        assertNull(CheckoutMerchantOrderIds.parseOrderId("PROD-5"));
        assertNull(CheckoutMerchantOrderIds.parseOrderId("CHK"));
    }
}
