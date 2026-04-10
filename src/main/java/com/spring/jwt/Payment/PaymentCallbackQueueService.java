package com.spring.jwt.Payment;

import com.spring.jwt.entity.PaymentCallbackQueue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentCallbackQueueService {

    private final PaymentCallbackQueueRepository callbackQueueRepository;
    private final PaymentAuditService auditService;

    @Transactional
    public Long enqueue(String paymentType, String callbackType, String encResp, String clientIp) {
        PaymentCallbackQueue item = PaymentCallbackQueue.builder()
                .paymentType(paymentType)
                .callbackType(callbackType)
                .encResp(encResp)
                .clientIp(clientIp)
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
        return saved.getId();
    }

    @Transactional(readOnly = true)
    public Optional<PaymentCallbackQueueStatusDTO> getStatus(Long queueId) {
        return callbackQueueRepository.findById(queueId).map(item ->
                PaymentCallbackQueueStatusDTO.builder()
                        .queueId(item.getId())
                        .paymentType(item.getPaymentType())
                        .callbackType(item.getCallbackType())
                        .status(item.getStatus())
                        .attemptCount(item.getAttemptCount())
                        .resultStatus(item.getResultStatus())
                        .orderId(item.getOrderId())
                        .message(item.getLastError())
                        .build()
        );
    }
}
