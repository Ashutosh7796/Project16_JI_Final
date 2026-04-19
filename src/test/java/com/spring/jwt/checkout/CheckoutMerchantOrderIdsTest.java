package com.spring.jwt.checkout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class CheckoutMerchantOrderIdsTest {

    @Test
    void generatesValidAmazonStyleOrderId() {
        String id1 = CheckoutMerchantOrderIds.forOrderId(42L);
        String id2 = CheckoutMerchantOrderIds.forOrderId(43L);
        
        // Ensure format matches \d{3}-\d{7}-\d{7}
        assertTrue(id1.matches("^\\d{3}-\\d{7}-\\d{7}$"), "ID 1 format is incorrect: " + id1);
        assertTrue(id2.matches("^\\d{3}-\\d{7}-\\d{7}$"), "ID 2 format is incorrect: " + id2);
        
        // Ensure some randomness (though theoretically they could collide, practically they won't in two calls)
        assertNotEquals(id1, id2);
    }
}
