package com.spring.jwt.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Security audit logging service to record security-related events
 * in a standardized format for compliance and monitoring
 */
@Component
public class SecurityAuditLogger {
    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");
    private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
    
    /**
     * Log authentication success event
     */
    public void logAuthenticationSuccess(String username, String source) {
        HttpServletRequest request = getCurrentRequest();
        String clientIp = getClientIp(request);
        String userAgent = getUserAgent(request);
        
        log.info("[AUTH_SUCCESS] user={}, source={}, ip={}, userAgent={}, timestamp={}",
                 username, source, clientIp, userAgent, getCurrentTimestamp());
    }
    
    /**
     * Log authentication failure event
     */
    public void logAuthenticationFailure(String username, String reason, String source) {
        HttpServletRequest request = getCurrentRequest();
        String clientIp = getClientIp(request);
        String userAgent = getUserAgent(request);
        
        log.warn("[AUTH_FAILURE] user={}, reason={}, source={}, ip={}, userAgent={}, timestamp={}",
                 username, reason, source, clientIp, userAgent, getCurrentTimestamp());
    }
    
    /**
     * Log access denied event
     */
    public void logAccessDenied(String username, String resource, String requiredAuthority) {
        HttpServletRequest request = getCurrentRequest();
        String clientIp = getClientIp(request);
        String userAgent = getUserAgent(request);
        
        log.warn("[ACCESS_DENIED] user={}, resource={}, requiredAuthority={}, ip={}, userAgent={}, timestamp={}",
                 username, resource, requiredAuthority, clientIp, userAgent, getCurrentTimestamp());
    }
    
    /**
     * Log token validity event
     */
    public void logTokenEvent(String type, String username, String tokenId, boolean isValid) {
        HttpServletRequest request = getCurrentRequest();
        String clientIp = request != null ? getClientIp(request) : "unknown";
        String userAgent = request != null ? getUserAgent(request) : "unknown";
        
        log.info("[TOKEN_{}] user={}, tokenId={}, valid={}, ip={}, userAgent={}, timestamp={}",
                 type, username, tokenId, isValid, clientIp, userAgent, getCurrentTimestamp());
    }
    
    /**
     * Log permission changes
     */
    public void logPermissionChange(String adminUser, String targetUser, String action, String permission) {
        HttpServletRequest request = getCurrentRequest();
        String clientIp = getClientIp(request);
        
        log.info("[PERMISSION_CHANGE] admin={}, target={}, action={}, permission={}, ip={}, timestamp={}",
                 adminUser, targetUser, action, permission, clientIp, getCurrentTimestamp());
    }
    
    /**
     * Log security configuration changes
     */
    public void logSecurityConfigChange(String adminUser, String configItem, String oldValue, String newValue) {
        HttpServletRequest request = getCurrentRequest();
        String clientIp = getClientIp(request);
        
        log.info("[CONFIG_CHANGE] admin={}, item={}, oldValue={}, newValue={}, ip={}, timestamp={}",
                 adminUser, configItem, oldValue, newValue, clientIp, getCurrentTimestamp());
    }
    
    /**
     * Get the current HTTP request if available
     */
    private HttpServletRequest getCurrentRequest() {
        return Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .filter(attrs -> attrs instanceof ServletRequestAttributes)
                .map(attrs -> ((ServletRequestAttributes) attrs).getRequest())
                .orElse(null);
    }
    
    /**
     * Extract the client IP address from the request
     */
    private String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty() && !xff.equalsIgnoreCase("unknown")) {
            // Get the first IP if multiple are present (client, proxy1, proxy2)
            return xff.contains(",") ? xff.split(",")[0].trim() : xff;
        }
        
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isEmpty() && !realIp.equalsIgnoreCase("unknown")) {
            return realIp;
        }
        
        return request.getRemoteAddr();
    }
    
    /**
     * Extract the user agent from the request
     */
    private String getUserAgent(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        
        String userAgent = request.getHeader("User-Agent");
        return userAgent != null ? userAgent : "unknown";
    }
    
    /**
     * Get current timestamp formatted for logs
     */
    private String getCurrentTimestamp() {
        return LocalDateTime.now().format(formatter);
    }
} 