package com.spring.jwt.checkout;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckoutRefundAuditService {

    private final CheckoutRefundAuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRED)
    public void log(Long refundId, Long adminId, String action, String details) {
        try {
            auditLogRepository.save(CheckoutRefundAuditLog.builder()
                    .refundId(refundId)
                    .adminId(adminId)
                    .action(truncate(action, 64))
                    .details(truncate(details, 8000))
                    .build());
        } catch (Exception e) {
            log.error("checkout refund audit log failed refundId={} action={}", refundId, action, e);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
