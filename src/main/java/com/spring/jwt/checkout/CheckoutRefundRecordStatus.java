package com.spring.jwt.checkout;

/**
 * CCAvenue refund automation lifecycle.
 */
public enum CheckoutRefundRecordStatus {
    /** Refund accepted by gateway (refund API returned success envelope). */
    INITIATED,
    /** Refund API failed, transport error, or order-status reconciliation still unclear — admin may intervene. */
    PENDING_GATEWAY,
    /** Refund confirmed (reconciliation or verified admin override). */
    SUCCESS,
    /** Refund definitively not processed (reconciliation or admin final decision). */
    FAILED
}
