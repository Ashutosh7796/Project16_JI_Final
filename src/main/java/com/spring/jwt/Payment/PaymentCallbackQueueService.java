package com.spring.jwt.Payment;

import com.spring.jwt.entity.PaymentCallbackQueue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
                .build();

        PaymentCallbackQueue saved = callbackQueueRepository.save(item);
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
