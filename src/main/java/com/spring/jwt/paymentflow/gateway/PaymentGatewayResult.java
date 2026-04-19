package com.spring.jwt.paymentflow.gateway;

/**
 * Outcome of a synchronous gateway step ({@code initiate}). Separates deterministic failures from
 * uncertain states — never map auth/signature errors to {@code PENDING_GATEWAY}.
 */
public sealed interface PaymentGatewayResult {

    /** Hard failure: mark payment FAILED immediately (no webhook will rescue). */
    record DeterministicFailure(String errorCode, String errorMessage) implements PaymentGatewayResult {}

    /** User should be sent to the gateway; we start the external completion clock. */
    record ProcessingPayload(String provisionalGatewayTxnId, String clientPayloadJson) implements PaymentGatewayResult {}

    /** Ambiguous / timeout from gateway client — reconcile or webhook will resolve. */
    record PendingGateway(String reason) implements PaymentGatewayResult {}
}
