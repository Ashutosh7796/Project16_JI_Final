package com.spring.jwt.checkout;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CheckoutScheduler {

    private final CheckoutService checkoutService;
    private final CheckoutRefundLifecycleService checkoutRefundLifecycleService;

    /**
     * Releases reservations for abandoned checkouts (UPI / net banking timeouts, closed tabs).
     */
    @Scheduled(cron = "${app.checkout.expire-cron:*/30 * * * * *}")
    @SchedulerLock(name = "checkoutReservationExpiry", lockAtLeastFor = "10s", lockAtMostFor = "5m")
    public void expireStalePaymentPending() {
        checkoutService.expireDuePaymentPendingOrders();
    }

    /**
     * Polls CCAvenue for orders stuck in PAYMENT_PENDING when webhooks are missing.
     */
    @Scheduled(cron = "${app.checkout.reconcile-cron:0 */2 * * * *}")
    @SchedulerLock(name = "checkoutPaymentReconciliation", lockAtLeastFor = "30s", lockAtMostFor = "9m")
    public void reconcilePayments() {
        checkoutService.reconcileStalePaymentPendingOrders();
    }

    /**
     * Resolves refund outcomes via CCAvenue order-status (primary after refund API).
     */
    @Scheduled(cron = "${app.checkout.refund-reconcile-cron:0 */3 * * * *}")
    @SchedulerLock(name = "checkoutRefundReconciliation", lockAtLeastFor = "20s", lockAtMostFor = "8m")
    public void reconcileRefunds() {
        checkoutRefundLifecycleService.reconcileOpenRefunds();
    }

    /**
     * EC-25: Cleans up PENDING orders that were never initiated (no payment attempt).
     * Runs hourly. These orders have no reservation and no TTL, so they accumulate forever without this.
     */
    @Scheduled(cron = "${app.checkout.orphan-pending-cron:0 0 * * * *}")
    @SchedulerLock(name = "checkoutOrphanPendingCleanup", lockAtLeastFor = "30s", lockAtMostFor = "5m")
    public void expireOrphanPendingOrders() {
        checkoutService.expireOrphanPendingOrders();
    }
}
