package com.spring.jwt.checkout;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class CheckoutRefundInitiationListener {

    private final CheckoutRefundLifecycleService checkoutRefundLifecycleService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRefundCreated(RefundCreatedEvent event) {
        try {
            checkoutRefundLifecycleService.initiateRefundApi(event.refundId(), false);
        } catch (Exception e) {
            log.error("Refund initiation after commit failed for refundId={}", event.refundId(), e);
        }
    }
}
