package com.spring.jwt.utils;

import com.spring.jwt.entity.SecurityAuditLog;
import com.spring.jwt.repository.SecurityAuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Security audit logging service — records events to both SLF4J logs AND
 * the {@code security_audit_log} database table for enterprise-grade traceability.
 * <p>
 * DB writes are async to avoid blocking filter/auth flows.
 * Old entries are purged after 90 days by default.
 */
@Component
public class SecurityAuditLogger {
    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");
    private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;

    /** Retention period for audit entries (days) */
    private static final int RETENTION_DAYS = 90;

    private final SecurityAuditLogRepository auditRepository;

    public SecurityAuditLogger(SecurityAuditLogRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    /**
     * Log authentication success event.
     */
    public void logAuthenticationSuccess(String username, String source) {
        HttpServletRequest request = getCurrentRequest();
        String clientIp = getClientIp(request);
        String userAgent = getUserAgent(request);
        
        log.info("[AUTH_SUCCESS] user={}, source={}, ip={}, userAgent={}, timestamp={}",
                 username, source, clientIp, userAgent, getCurrentTimestamp());

        persistAsync("AUTH_SUCCESS", username, clientIp, userAgent, request, null,
                "source=" + source, true);
    }
    
    /**
     * Log authentication failure event.
     */
    public void logAuthenticationFailure(String username, String reason, String source) {
        HttpServletRequest request = getCurrentRequest();
        String clientIp = getClientIp(request);
        String userAgent = getUserAgent(request);
        
        log.warn("[AUTH_FAILURE] user={}, reason={}, source={}, ip={}, userAgent={}, timestamp={}",
                 username, reason, source, clientIp, userAgent, getCurrentTimestamp());

        persistAsync("AUTH_FAILURE", username, clientIp, userAgent, request, null,
                "reason=" + reason + ", source=" + source, false);
    }
    
    /**
     * Log access denied event.
     */
    public void logAccessDenied(String username, String resource, String requiredAuthority) {
        HttpServletRequest request = getCurrentRequest();
        String clientIp = getClientIp(request);
        String userAgent = getUserAgent(request);
        
        log.warn("[ACCESS_DENIED] user={}, resource={}, requiredAuthority={}, ip={}, userAgent={}, timestamp={}",
                 username, resource, requiredAuthority, clientIp, userAgent, getCurrentTimestamp());

        persistAsync("ACCESS_DENIED", username, clientIp, userAgent, request, null,
                "resource=" + resource + ", required=" + requiredAuthority, false);
    }
    
    /**
     * Log token validity event (blacklist, reuse attempt, etc.).
     */
    public void logTokenEvent(String type, String username, String tokenId, boolean isValid) {
        HttpServletRequest request = getCurrentRequest();
        String clientIp = request != null ? getClientIp(request) : "unknown";
        String userAgent = request != null ? getUserAgent(request) : "unknown";
        
        log.info("[TOKEN_{}] user={}, tokenId={}, valid={}, ip={}, userAgent={}, timestamp={}",
                 type, username, tokenId, isValid, clientIp, userAgent, getCurrentTimestamp());

        persistAsync("TOKEN_" + type, username, clientIp, userAgent, request, tokenId,
                "valid=" + isValid, isValid);
    }

    /**
     * Log logout event.
     */
    public void logLogout(String username) {
        HttpServletRequest request = getCurrentRequest();
        String clientIp = getClientIp(request);
        String userAgent = getUserAgent(request);

        log.info("[LOGOUT] user={}, ip={}, userAgent={}, timestamp={}",
                username, clientIp, userAgent, getCurrentTimestamp());

        persistAsync("LOGOUT", username, clientIp, userAgent, request, null, null, true);
    }
    
    /**
     * Log permission changes.
     */
    public void logPermissionChange(String adminUser, String targetUser, String action, String permission) {
        HttpServletRequest request = getCurrentRequest();
        String clientIp = getClientIp(request);
        
        log.info("[PERMISSION_CHANGE] admin={}, target={}, action={}, permission={}, ip={}, timestamp={}",
                 adminUser, targetUser, action, permission, clientIp, getCurrentTimestamp());

        persistAsync("PERMISSION_CHANGE", adminUser, clientIp, null, request, null,
                "target=" + targetUser + ", action=" + action + ", permission=" + permission, true);
    }
    
    /**
     * Log security configuration changes.
     */
    public void logSecurityConfigChange(String adminUser, String configItem, String oldValue, String newValue) {
        HttpServletRequest request = getCurrentRequest();
        String clientIp = getClientIp(request);
        
        log.info("[CONFIG_CHANGE] admin={}, item={}, oldValue={}, newValue={}, ip={}, timestamp={}",
                 adminUser, configItem, oldValue, newValue, clientIp, getCurrentTimestamp());

        persistAsync("CONFIG_CHANGE", adminUser, clientIp, null, request, null,
                "item=" + configItem + ", old=" + oldValue + ", new=" + newValue, true);
    }

    /**
     * Purge audit entries older than retention period (runs daily at 3 AM).
     * ShedLock ensures only one pod runs this in multi-instance deployments.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @SchedulerLock(name = "auditLogPurge", lockAtLeastFor = "30s", lockAtMostFor = "15m")
    public void purgeOldEntries() {
        Instant cutoff = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);
        try {
            int deleted = auditRepository.deleteOlderThan(cutoff);
            if (deleted > 0) {
                log.info("Purged {} audit entries older than {} days", deleted, RETENTION_DAYS);
            }
        } catch (Exception e) {
            log.warn("Error purging old audit entries: {}", e.getMessage());
        }
    }

    // ─── Internal ───────────────────────────────────────────────────────────

    /**
     * Persist audit entry to DB asynchronously to avoid blocking request processing.
     */
    @Async
    protected void persistAsync(String eventType, String username, String clientIp,
                                String userAgent, HttpServletRequest request,
                                String tokenId, String detail, boolean success) {
        try {
            SecurityAuditLog entry = SecurityAuditLog.of(eventType, username, success);
            entry.setClientIp(clientIp);
            entry.setUserAgent(truncate(userAgent, 512));
            entry.setTokenId(tokenId);
            entry.setDetail(truncate(detail, 2000));
            if (request != null) {
                entry.setRequestUri(truncate(request.getRequestURI(), 1024));
                entry.setRequestMethod(request.getMethod());
            }
            auditRepository.save(entry);
        } catch (Exception e) {
            // Never let audit persistence failure break the main flow
            log.debug("Failed to persist audit entry: {}", e.getMessage());
        }
    }

    private String truncate(String value, int maxLen) {
        if (value == null) return null;
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }

    private HttpServletRequest getCurrentRequest() {
        return Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .filter(attrs -> attrs instanceof ServletRequestAttributes)
                .map(attrs -> ((ServletRequestAttributes) attrs).getRequest())
                .orElse(null);
    }
    
    private String getClientIp(HttpServletRequest request) {
        if (request == null) return "unknown";
        
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty() && !xff.equalsIgnoreCase("unknown")) {
            return xff.contains(",") ? xff.split(",")[0].trim() : xff;
        }
        
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isEmpty() && !realIp.equalsIgnoreCase("unknown")) {
            return realIp;
        }
        
        return request.getRemoteAddr();
    }
    
    private String getUserAgent(HttpServletRequest request) {
        if (request == null) return "unknown";
        String userAgent = request.getHeader("User-Agent");
        return userAgent != null ? userAgent : "unknown";
    }
    
    private String getCurrentTimestamp() {
        return LocalDateTime.now().format(formatter);
    }
}