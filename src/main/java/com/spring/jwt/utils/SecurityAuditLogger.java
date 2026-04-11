package com.spring.jwt.utils;

import com.spring.jwt.audit.ApplicationAuditService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Security audit: structured logs plus {@link ApplicationAuditService} rows for production forensics.
 */
@Component
@RequiredArgsConstructor
public class SecurityAuditLogger {
    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");
    private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;

    private final ApplicationAuditService applicationAuditService;

    public void logAuthenticationSuccess(String username, String source) {
        HttpServletRequest request = getCurrentRequest();
        String clientIp = getClientIp(request);
        String userAgent = getUserAgent(request);

        log.info("[AUTH_SUCCESS] user={}, source={}, ip={}, userAgent={}, timestamp={}",
                username, source, clientIp, userAgent, getCurrentTimestamp());

        applicationAuditService.log(
                "SECURITY",
                "AUTH_SUCCESS",
                "SUCCESS",
                null,
                maskActor(username),
                "USER",
                null,
                "source=" + source,
                clientIp,
                userAgent
        );
    }

    public void logAuthenticationFailure(String username, String reason, String source) {
        HttpServletRequest request = getCurrentRequest();
        String clientIp = getClientIp(request);
        String userAgent = getUserAgent(request);

        log.warn("[AUTH_FAILURE] user={}, reason={}, source={}, ip={}, userAgent={}, timestamp={}",
                username, reason, source, clientIp, userAgent, getCurrentTimestamp());

        applicationAuditService.log(
                "SECURITY",
                "AUTH_FAILURE",
                "FAILURE",
                null,
                maskActor(username),
                "USER",
                null,
                "source=" + source + ";reason=" + truncate(reason, 500),
                clientIp,
                userAgent
        );
    }

    public void logAccessDenied(String username, String resource, String requiredAuthority) {
        HttpServletRequest request = getCurrentRequest();
        String clientIp = getClientIp(request);
        String userAgent = getUserAgent(request);

        log.warn("[ACCESS_DENIED] user={}, resource={}, requiredAuthority={}, ip={}, userAgent={}, timestamp={}",
                username, resource, requiredAuthority, clientIp, userAgent, getCurrentTimestamp());

        applicationAuditService.log(
                "SECURITY",
                "ACCESS_DENIED",
                "FAILURE",
                null,
                maskActor(username),
                "HTTP",
                resource,
                "requiredAuthority=" + requiredAuthority,
                clientIp,
                userAgent
        );
    }

    public void logTokenEvent(String type, String username, String tokenId, boolean isValid) {
        HttpServletRequest request = getCurrentRequest();
        String clientIp = request != null ? getClientIp(request) : "unknown";
        String userAgent = request != null ? getUserAgent(request) : "unknown";

        log.info("[TOKEN_{}] user={}, tokenId={}, valid={}, ip={}, userAgent={}, timestamp={}",
                type, username, tokenId, isValid, clientIp, userAgent, getCurrentTimestamp());

        applicationAuditService.log(
                "SECURITY",
                "TOKEN_" + type,
                isValid ? "SUCCESS" : "FAILURE",
                null,
                maskActor(username),
                "TOKEN",
                tokenId,
                null,
                clientIp,
                userAgent
        );
    }

    public void logPermissionChange(String adminUser, String targetUser, String action, String permission) {
        HttpServletRequest request = getCurrentRequest();
        String clientIp = getClientIp(request);
        String userAgent = getUserAgent(request);

        log.info("[PERMISSION_CHANGE] admin={}, target={}, action={}, permission={}, ip={}, timestamp={}",
                adminUser, targetUser, action, permission, clientIp, getCurrentTimestamp());

        applicationAuditService.log(
                "SECURITY",
                "PERMISSION_CHANGE",
                "SUCCESS",
                null,
                maskActor(adminUser),
                "USER",
                maskActor(targetUser),
                "action=" + action + ";permission=" + permission,
                clientIp,
                userAgent
        );
    }

    public void logSecurityConfigChange(String adminUser, String configItem, String oldValue, String newValue) {
        HttpServletRequest request = getCurrentRequest();
        String clientIp = getClientIp(request);
        String userAgent = getUserAgent(request);

        log.info("[CONFIG_CHANGE] admin={}, item={}, oldValue={}, newValue={}, ip={}, timestamp={}",
                adminUser, configItem, oldValue, newValue, clientIp, getCurrentTimestamp());

        applicationAuditService.log(
                "SECURITY",
                "CONFIG_CHANGE",
                "SUCCESS",
                null,
                maskActor(adminUser),
                "CONFIG",
                configItem,
                "oldValue=" + truncate(oldValue, 200) + ";newValue=" + truncate(newValue, 200),
                clientIp,
                userAgent
        );
    }

    private static String maskActor(String username) {
        if (username == null) {
            return null;
        }
        if (username.contains("@")) {
            return DataMaskingUtils.maskEmail(username);
        }
        return username.length() > 120 ? username.substring(0, 120) : username;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private HttpServletRequest getCurrentRequest() {
        return Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .filter(attrs -> attrs instanceof ServletRequestAttributes)
                .map(attrs -> ((ServletRequestAttributes) attrs).getRequest())
                .orElse(null);
    }

    private String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }

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
        if (request == null) {
            return "unknown";
        }

        String userAgent = request.getHeader("User-Agent");
        return userAgent != null ? userAgent : "unknown";
    }

    private String getCurrentTimestamp() {
        return LocalDateTime.now().format(formatter);
    }
}
