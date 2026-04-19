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
    List<CheckoutOrderResponse> listMyCheckoutOrders(Long userId, int limit);

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

    /**
     * Emergency fail-fast: when the cancel-URL callback arrives with NO encrypted response
     * (e.g. 10002 merchant auth failed), fail the most recent PAYMENT_PENDING order immediately.
     */
    void failLatestPendingOrderOnGatewayCancel(String clientIp);

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
    
    /** Marks all pending line items as fulfilled for a PAID order. */
    CheckoutOrderResponse adminFulfillOrder(Long orderId, Long adminId);

    /** Admin: list all checkout orders across all users (newest first). */
    List<CheckoutOrderResponse> adminListAllOrders(int limit);
}
