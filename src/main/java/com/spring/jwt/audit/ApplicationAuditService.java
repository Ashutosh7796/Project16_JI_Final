package com.spring.jwt.audit;

import com.spring.jwt.entity.ApplicationAuditLog;
import com.spring.jwt.repository.ApplicationAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists high-value business and security events. Uses {@code REQUIRES_NEW} so audit failures never roll back caller work.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApplicationAuditService {

    private static final int MAX_DETAILS = 8000;
    private static final int MAX_USER_AGENT = 500;

    private final ApplicationAuditLogRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String category, String actionType, String outcome, Long actorUserId, String actorLabel,
                    String resourceType, String resourceId, String details, String ipAddress, String userAgent) {
        try {
            ApplicationAuditLog row = ApplicationAuditLog.builder()
                    .category(truncate(category, 30))
                    .actionType(truncate(actionType, 64))
                    .outcome(truncate(outcome, 16))
                    .actorUserId(actorUserId)
                    .actorLabel(truncate(actorLabel, 255))
                    .resourceType(resourceType != null ? truncate(resourceType, 64) : null)
                    .resourceId(resourceId != null ? truncate(resourceId, 128) : null)
                    .details(truncate(details, MAX_DETAILS))
                    .ipAddress(ipAddress != null ? truncate(ipAddress, 45) : null)
                    .userAgent(userAgent != null ? truncate(userAgent, MAX_USER_AGENT) : null)
                    .build();
            repository.save(row);
        } catch (Exception e) {
            log.error("Application audit persist failed: category={}, action={}, error={}",
                    category, actionType, e.getMessage());
        }
    }

    private static String truncate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }
}
