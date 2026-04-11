package com.spring.jwt.jwt;

import com.spring.jwt.utils.SecurityAuditLogger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;

/**
 * Service to manage blacklisted tokens to prevent token reuse
 * This is a security feature used by major companies to prevent refresh token replay attacks
 */
@Service
@Slf4j
public class TokenBlacklistService {
    
    @Autowired(required = false)
    private SecurityAuditLogger securityAuditLogger;

    private final Map<String, TokenInfo> blacklistedTokens = new ConcurrentHashMap<>();

    private final AtomicInteger totalBlacklistedTokens = new AtomicInteger(0);
    private final AtomicInteger blacklistHits = new AtomicInteger(0);
    
    /**
     * Add a token to the blacklist
     * @param tokenId The JWT ID (jti) to blacklist
     * @param expirationTime When the token expires and can be removed from blacklist
     * @param username The username associated with the token (if available)
     * @param reason Why the token was blacklisted (logout, rotation, etc.)
     */
    public void blacklistToken(String tokenId, Instant expirationTime, String username, String reason) {
        log.debug("Blacklisting token: {}", tokenId);
        
        TokenInfo tokenInfo = new TokenInfo(expirationTime, username, reason, Instant.now());
        blacklistedTokens.put(tokenId, tokenInfo);
        totalBlacklistedTokens.incrementAndGet();

        if (securityAuditLogger != null) {
            securityAuditLogger.logTokenEvent("BLACKLIST", username, maskTokenId(tokenId), true);
        }
    }
    
    /**
     * Add a token to the blacklist
     * @param tokenId The JWT ID (jti) to blacklist
     * @param expirationTime When the token expires and can be removed from blacklist
     */
    public void blacklistToken(String tokenId, Instant expirationTime) {
        blacklistToken(tokenId, expirationTime, "unknown", "token_rotation");
    }
    
    /**
     * Check if a token is blacklisted
     * @param tokenId The JWT ID (jti) to check
     * @return true if the token is blacklisted
     */
    public boolean isBlacklisted(String tokenId) {
        boolean result = blacklistedTokens.containsKey(tokenId);
        if (result) {
            blacklistHits.incrementAndGet();
            TokenInfo info = blacklistedTokens.get(tokenId);

            if (info != null) {
                info.incrementReuseAttempts();

                log.warn("Attempted reuse of blacklisted token ID: {}, user: {}, blacklisted at: {}, reuse attempts: {}",
                    maskTokenId(tokenId), info.getUsername(), info.getBlacklistedAt(), info.getReuseAttempts());

                if (securityAuditLogger != null && info.getReuseAttempts() > 1) {
                    securityAuditLogger.logTokenEvent("REUSE_ATTEMPT", info.getUsername(), maskTokenId(tokenId), false);
                }
            }
        }
        return result;
    }
    
    /**
     * Clean up expired tokens from the blacklist every hour
     */
    @Scheduled(fixedRate = 3600000)
    public void cleanupBlacklist() {
        log.debug("Cleaning up token blacklist");
        Instant now = Instant.now();
        int initialSize = blacklistedTokens.size();
        
        blacklistedTokens.entrySet().removeIf(entry -> entry.getValue().getExpirationTime().isBefore(now));
        
        int removedCount = initialSize - blacklistedTokens.size();
        if (removedCount > 0) {
            log.info("Removed {} expired tokens from blacklist", removedCount);
        }
    }
    
    /**
     * Get statistics about the token blacklist
     */
    public BlacklistStats getStats() {
        return new BlacklistStats(
            blacklistedTokens.size(),
            totalBlacklistedTokens.get(),
            blacklistHits.get()
        );
    }
    
    /**
     * Extend the blacklisting period for a token 
     * (useful for high-risk scenarios where a token needs to be blacklisted longer)
     */
    public void extendBlacklisting(String tokenId, long additionalHours) {
        TokenInfo info = blacklistedTokens.get(tokenId);
        if (info != null) {
            Instant newExpiration = info.getExpirationTime().plus(additionalHours, ChronoUnit.HOURS);
            info.setExpirationTime(newExpiration);
            log.info("Extended blacklisting for token: {}, new expiration: {}", maskTokenId(tokenId), newExpiration);
        }
    }
    
    /**
     * Mask token ID for logging (security)
     */
    private String maskTokenId(String tokenId) {
        if (tokenId == null || tokenId.length() < 8) {
            return "***";
        }
        int length = tokenId.length();
        return tokenId.substring(0, 3) + "..." + tokenId.substring(length - 3);
    }
    
    /**
     * Information about a blacklisted token
     */
    private static class TokenInfo {
        private Instant expirationTime;
        private final String username;
        private final String reason;
        private final Instant blacklistedAt;
        private int reuseAttempts = 0;
        
        public TokenInfo(Instant expirationTime, String username, String reason, Instant blacklistedAt) {
            this.expirationTime = expirationTime;
            this.username = username;
            this.reason = reason;
            this.blacklistedAt = blacklistedAt;
        }
        
        public Instant getExpirationTime() {
            return expirationTime;
        }
        
        public void setExpirationTime(Instant expirationTime) {
            this.expirationTime = expirationTime;
        }
        
        public String getUsername() {
            return username;
        }
        
        public String getReason() {
            return reason;
        }
        
        public Instant getBlacklistedAt() {
            return blacklistedAt;
        }
        
        public int getReuseAttempts() {
            return reuseAttempts;
        }
        
        public void incrementReuseAttempts() {
            this.reuseAttempts++;
        }
    }
    
    /**
     * Statistics about the blacklist
     */
    public static class BlacklistStats {
        private final int currentSize;
        private final int totalBlacklisted;
        private final int blacklistHits;
        
        public BlacklistStats(int currentSize, int totalBlacklisted, int blacklistHits) {
            this.currentSize = currentSize;
            this.totalBlacklisted = totalBlacklisted;
            this.blacklistHits = blacklistHits;
        }
        
        public int getCurrentSize() {
            return currentSize;
        }
        
        public int getTotalBlacklisted() {
            return totalBlacklisted;
        }
        
        public int getBlacklistHits() {
            return blacklistHits;
        }
    }
} 