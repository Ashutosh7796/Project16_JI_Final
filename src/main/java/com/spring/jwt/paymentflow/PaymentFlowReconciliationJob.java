package com.spring.jwt.paymentflow;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Time-based safety net when webhooks are delayed or users abandon checkout. Does not replace webhooks
 * as source of truth for SUCCESS — only fails stuck uncertain rows.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentFlowReconciliationJob {

    private final PaymentFlowService paymentFlowService;

    @Scheduled(fixedDelayString = "${app.payment-flow.reconcile-fixed-delay-ms:60000}")
    @SchedulerLock(name = "PaymentFlowReconciliationJob_reconcileStalePayments", lockAtMostFor = "9m", lockAtLeastFor = "30s")
    public void reconcileStalePayments() {
        try {
            paymentFlowService.reconcileStalePayments();
        } catch (Exception e) {
            log.error("payment-flow reconcile job failed", e);
        }
    }
}
