package com.spring.jwt.checkout;

import com.spring.jwt.checkout.dto.CheckoutOrderResponse;
import com.spring.jwt.checkout.dto.CreateCheckoutOrderRequest;
import com.spring.jwt.checkout.dto.InitiateCheckoutPaymentResponse;

import java.util.List;
import java.util.Map;

public interface CheckoutService {

    CheckoutOrderResponse createOrder(Long userId, CreateCheckoutOrderRequest request, String checkoutIdempotencyKey);

    InitiateCheckoutPaymentResponse initiatePayment(Long userId, Long orderId, String paymentInitIdempotencyKey);

    /**
     * Persists FAILED (or refreshes failReason) after the payment form could not be built (e.g. gateway config).
     * Intended for use after the initiating transaction rolled back.
     */
    void recordCheckoutPaymentPreparationFailure(Long userId, Long orderId, String reason);

    CheckoutOrderResponse getOrder(Long userId, Long orderId);
    
    CheckoutOrderResponse adminGetOrder(Long orderId);
    
    void syncPaymentStatusIfPending(Long userId, Long orderId);

    /** Recent checkout orders for the signed-in user (newest first). */
    org.springframework.data.domain.Page<CheckoutOrderResponse> listMyCheckoutOrders(Long userId, org.springframework.data.domain.Pageable pageable);

    /**
     * Called when the frontend detects user abandonment (tab close, navigation away).
     * If order is PAYMENT_PENDING and not yet PAID, releases reservations and marks FAILED.
     * Safe to call multiple times (idempotent).
     */
    void abandonOrder(Long userId, Long orderId);

    /**
     * Unauthenticated abandon via {@code navigator.sendBeacon}. Validates ownership with
     * {@code abandonToken} instead of JWT userId. Idempotent / fire-and-forget safe.
     */
    void abandonOrderByToken(Long orderId, String abandonToken);

    /** Customer abandons checkout: PENDING (no payment) or PAYMENT_PENDING (releases reservations). */
    void cancelOrder(Long userId, Long orderId);

    /**
     * Customer-initiated full (remaining) refund for a PAID order.
     */
    void requestCustomerRefund(Long userId, Long orderId, String reason);

    /**
     * Primary payment outcome path: decrypted CCAvenue callback (verified by successful decrypt + field presence).
     */
    void handleCcAvenueDecryptedCallback(Map<String, String> params, String encResp, String clientIp);

    void expireDuePaymentPendingOrders();

    /** EC-25: Expire PENDING orders older than 24h that were never initiated. */
    void expireOrphanPendingOrders();

    /**
     * @deprecated Unsafe under concurrency (could fail the wrong user's order). Now a structured
     * no-op kept only for binary compatibility. Use
     * {@link #failPaymentPendingByMerchantOrderId(String, String, String)} with an HMAC-verified
     * merchantOrderId taken from the {@code cmref} hint instead.
     */
    @Deprecated
    void failLatestPendingOrderOnGatewayCancel(String clientIp);

    /**
     * Scoped fail-fast on a specific merchantOrderId after the cancel-URL callback arrived
     * with no payload (CCAvenue 10002 merchant-auth failure). Caller MUST already have
     * verified the merchantOrderId with {@link #verifyCancelHint(String)}. Idempotent —
     * silently no-ops if the order is no longer PAYMENT_PENDING.
     */
    void failPaymentPendingByMerchantOrderId(String merchantOrderId, String reason, String clientIp);

    /**
     * Verify the {@code cmref} hint posted to the cancel callback. Returns the original
     * merchantOrderId only on a valid, in-date HMAC. Encapsulates the secret-resolution chain
     * (configured property → ephemeral per-JVM fallback) so controllers cannot drift apart.
     */
    java.util.Optional<String> verifyCancelHint(String token);

    void reconcileStalePaymentPendingOrders();

    /**
     * Marks a PAID checkout order as REFUNDED after a refund is completed (manual or gateway).
     */
    void adminMarkRefunded(Long orderId, Long adminId, String notes);

    /**
     * Admin-initiated order cancellation. Supports any cancellable status (PENDING, PAYMENT_PENDING, PAID).
     * For PAID orders, automatically initiates a refund to the original payment method.
     */
    void adminCancelOrder(Long orderId, Long adminId, String reason);
    
    /** Marks all active line items to a specific target status. */
    CheckoutOrderResponse adminUpdateFulfillment(Long orderId, Long adminId, CheckoutLineFulfillmentStatus targetStatus);

    /** Admin: list all checkout orders across all users (newest first). */
    List<CheckoutOrderResponse> adminListAllOrders(int limit);
}
