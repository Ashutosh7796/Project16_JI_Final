package com.spring.jwt.Payment;

import com.spring.jwt.entity.PaymentCallbackQueue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Event-driven (primary) + scheduled fallback (secondary) orchestration for payment callbacks.
 * Heavy work runs in {@link PaymentCallbackProcessingService} so transactions and {@code REQUIRES_NEW} behave correctly.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentCallbackQueueProcessor {

    private static final List<String> PROCESSABLE_STATUSES = Arrays.asList("PENDING", "RETRY");

    private final PaymentCallbackQueueRepository queueRepository;
    private final PaymentCallbackProcessingService processingService;

    @Value("${payment.callback.queue.batch-size:20}")
    private int batchSize;

    @Value("${payment.callback.queue.stale-processing-minutes:10}")
    private int staleProcessingMinutes;

    @Async("paymentCallbackExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentCallbackEvent(PaymentCallbackEvent event) {
        log.debug("Event-driven processing triggered for queueId={}", event.getQueueId());
        processingService.processByQueueId(event.getQueueId());
    }

    /**
     * Fallback: retries and recovery without holding one long transaction around the batch.
     */
    @Scheduled(fixedDelayString = "${payment.callback.queue.poll-delay-ms:30000}")
    public void processQueue() {
        processingService.releaseStaleProcessingRows(staleProcessingMinutes);

        List<PaymentCallbackQueue> items = queueRepository
                .findByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                        PROCESSABLE_STATUSES, LocalDateTime.now(), PageRequest.of(0, batchSize));

        if (!items.isEmpty()) {
            log.info("Scheduled fallback processing {} pending/retry items", items.size());
            for (PaymentCallbackQueue item : items) {
                processingService.processByQueueId(item.getId());
            }
        }
    }
}
