package com.spring.jwt.Payment;

import com.spring.jwt.entity.PaymentCallbackQueue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentCallbackQueueService {

    private final PaymentCallbackQueueRepository callbackQueueRepository;
    private final PaymentAuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public PaymentCallbackEnqueueResult enqueue(String paymentType, String callbackType, String encResp, String clientIp) {
        return enqueue(paymentType, callbackType, encResp, clientIp, null);
    }

    /**
     * Overload that pre-populates the {@code orderId} column with a hint already validated by the
     * caller (e.g. an HMAC-verified {@code cmref} for the empty cancel-callback path). The
     * processor reads this hint to dispatch a scoped fail instead of guessing.
     */
    @Transactional
    public PaymentCallbackEnqueueResult enqueue(String paymentType, String callbackType, String encResp,
                                                String clientIp, String knownMerchantOrderId) {
        // P2.3: dedup gateway POSTs by payload hash. CCAvenue can deliver the same callback
        // twice (network retry / browser reload) — we must not enqueue two work units for the
        // same captured event. Empty-encResp cancels still need attribution, so when no encResp
        // is present we fold the merchantOrderId hint and clientIp into the hash to keep
        // distinct user attempts separable while still collapsing actual duplicates.
        String payloadHash = computePayloadHash(paymentType, callbackType, encResp,
                knownMerchantOrderId, clientIp);
        Optional<PaymentCallbackQueue> existing = callbackQueueRepository.findByPayloadHash(payloadHash);
        if (existing.isPresent()) {
            PaymentCallbackQueue prior = existing.get();
            log.info("Dedup: returning existing callback queue row queueId={} status={} for repeated payload",
                    prior.getId(), prior.getStatus());
            return new PaymentCallbackEnqueueResult(prior.getId(), prior.getStatusToken());
        }

        String statusToken = UUID.randomUUID().toString().replace("-", "");
        PaymentCallbackQueue item = PaymentCallbackQueue.builder()
                .paymentType(paymentType)
                .callbackType(callbackType)
                .encResp(encResp)
                .clientIp(clientIp)
                .statusToken(statusToken)
                .status("PENDING")
                .attemptCount(0)
                .nextAttemptAt(LocalDateTime.now())
                .orderId(knownMerchantOrderId)
                .payloadHash(payloadHash)
                .build();

        PaymentCallbackQueue saved;
        try {
            saved = callbackQueueRepository.save(item);
        } catch (DataIntegrityViolationException dup) {
            // Race lost to a concurrent enqueue with the same hash — fall through to dedup return.
            PaymentCallbackQueue prior = callbackQueueRepository.findByPayloadHash(payloadHash)
                    .orElseThrow(() -> dup);
            log.info("Dedup race: race-loss returning queueId={} for payload hash collision", prior.getId());
            return new PaymentCallbackEnqueueResult(prior.getId(), prior.getStatusToken());
        }
        auditService.logEvent(
                paymentType,
                null,
                null,
                "CALLBACK_QUEUED",
                null,
                "PENDING",
                null,
                clientIp,
                "callbackType=" + callbackType + ", queueId=" + saved.getId()
        );
        log.info("Queued payment callback: queueId={}, paymentType={}, callbackType={}",
                saved.getId(), paymentType, callbackType);

        // Publish event for immediate processing (event-driven approach)
        eventPublisher.publishEvent(new PaymentCallbackEvent(this, saved.getId(), paymentType, callbackType));

        return new PaymentCallbackEnqueueResult(saved.getId(), saved.getStatusToken());
    }

    @Transactional(readOnly = true)
    public Optional<PaymentCallbackQueueStatusDTO> getStatusByPublicToken(String rawToken) {
        String token = PaymentInputSanitizer.sanitizeOpaqueToken(rawToken);
        if (token.isEmpty()) {
            return Optional.empty();
        }
        return callbackQueueRepository.findByStatusToken(token).map(this::toStatusDto);
    }

    @Transactional(readOnly = true)
    public Optional<PaymentCallbackQueueStatusDTO> getStatus(Long queueId) {
        return callbackQueueRepository.findById(queueId).map(this::toStatusDto);
    }

    private static String computePayloadHash(String paymentType, String callbackType, String encResp,
                                             String knownMerchantOrderId, String clientIp) {
        StringBuilder sb = new StringBuilder(256);
        sb.append(paymentType == null ? "" : paymentType).append('|');
        sb.append(callbackType == null ? "" : callbackType).append('|');
        if (encResp != null && !encResp.isBlank()) {
            sb.append("E:").append(encResp);
        } else {
            sb.append("M:").append(knownMerchantOrderId == null ? "" : knownMerchantOrderId);
            sb.append("|I:").append(clientIp == null ? "" : clientIp);
            // Cancel-without-data should still produce one queue row per minute per (order, ip)
            // so a burst from the browser reload doesn't get duplicated, but a totally separate
            // user retry later still enqueues fresh work.
            sb.append("|T:").append(System.currentTimeMillis() / 60_000L);
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private PaymentCallbackQueueStatusDTO toStatusDto(PaymentCallbackQueue item) {
        return PaymentCallbackQueueStatusDTO.builder()
                .queueId(item.getId())
                .paymentType(item.getPaymentType())
                .callbackType(item.getCallbackType())
                .status(item.getStatus())
                .attemptCount(item.getAttemptCount())
                .resultStatus(item.getResultStatus())
                .orderId(item.getOrderId())
                .message(item.getLastError())
                .build();
    }
}
