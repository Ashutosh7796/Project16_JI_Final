package com.spring.jwt.Payment;

import com.spring.jwt.entity.PaymentAuditLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentAuditService {

    private final PaymentAuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEvent(String paymentType, Long paymentId, String ccavenueOrderId,
                         String actionType, String oldStatus, String newStatus,
                         Long actionByUserId, String ipAddress, String details) {
        try {
            PaymentAuditLog auditLog = PaymentAuditLog.builder()
                    .paymentType(paymentType)
                    .paymentId(paymentId)
                    .ccavenueOrderId(ccavenueOrderId)
                    .actionType(actionType)
                    .oldStatus(oldStatus)
                    .newStatus(newStatus)
                    .actionByUserId(actionByUserId)
                    .ipAddress(ipAddress)
                    .details(truncateDetails(details))
                    .actionAt(LocalDateTime.now())
                    .build();

            auditLogRepository.save(auditLog);

            log.info("Payment audit: type={}, paymentId={}, action={}, status={}->{}",
                    paymentType, paymentId, actionType, oldStatus, newStatus);
        } catch (Exception e) {
            log.error("Failed to write payment audit log: type={}, paymentId={}, action={}",
                    paymentType, paymentId, actionType, e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logFraudEvent(String paymentType, Long paymentId, String ccavenueOrderId,
                              Long userId, String ipAddress, String reason) {
        logEvent(paymentType, paymentId, ccavenueOrderId,
                "FRAUD_FLAGGED", null, null, userId, ipAddress, reason);
        log.warn("FRAUD FLAGGED: type={}, paymentId={}, orderId={}, userId={}, ip={}, reason={}",
                paymentType, paymentId, ccavenueOrderId, userId, ipAddress, reason);
    }

    private String truncateDetails(String details) {
        if (details == null) return null;
        return details.length() > 2000 ? details.substring(0, 2000) : details;
    }
}
