package com.spring.jwt.jwt;

import com.spring.jwt.entity.BlacklistedToken;
import com.spring.jwt.repository.BlacklistedTokenRepository;
import com.spring.jwt.utils.SecurityAuditLogger;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;

/**
 * Service to manage blacklisted tokens to prevent token reuse.
 * <p>
 * Tokens are persisted to the {@code blacklisted_tokens} table (via JPA) so
 * the blacklist survives server restarts and works across multiple pods.
 * An in-memory {@link ConcurrentHashMap} serves as an L1 cache for hot lookups
 * to avoid hitting the DB on every request.
 */
@Service
@Slf4j
public class TokenBlacklistService {

    private final BlacklistedTokenRepository repository;
    private final SecurityAuditLogger securityAuditLogger;

    /** L1 in-memory cache — prevents DB hit on every isBlacklisted() call. */
    private final Map<String, Instant> localCache = new ConcurrentHashMap<>();

    private final AtomicInteger totalBlacklistedTokens = new AtomicInteger(0);
    private final AtomicInteger blacklistHits = new AtomicInteger(0);

    public TokenBlacklistService(BlacklistedTokenRepository repository,
                                  SecurityAuditLogger securityAuditLogger) {
        this.repository = repository;
        this.securityAuditLogger = securityAuditLogger;
    }

    /**
     * Blacklist a token — persists to DB + adds to L1 cache.
     *
     * @param tokenId        JWT jti claim
     * @param expirationTime when the JWT expires (safe to purge after)
     * @param username       associated user
     * @param reason         why blacklisted (logout, token_rotation, security_revoke)
     */
    public void blacklistToken(String tokenId, Instant expirationTime, String username, String reason) {
        blacklistToken(tokenId, expirationTime, username, reason, "unknown");
    }

    /**
     * Blacklist a token with token type info.
     */
    public void blacklistToken(String tokenId, Instant expirationTime, String username,
                                String reason, String tokenType) {
        if (tokenId == null) return;

        log.debug("Blacklisting token: {} type={} reason={}", maskTokenId(tokenId), tokenType, reason);

        try {
            // Persist to DB (idempotent via unique constraint)
            if (!repository.existsByTokenId(tokenId)) {
                BlacklistedToken entity = new BlacklistedToken(
                        tokenId, tokenType, username, reason, expirationTime);
                repository.save(entity);
            }
        } catch (Exception e) {
            // Unique constraint violation = already blacklisted = fine
            log.debug("Token already blacklisted or DB error: {}", e.getMessage());
        }

        // Always update L1 cache
        localCache.put(tokenId, expirationTime);
        totalBlacklistedTokens.incrementAndGet();

        if (securityAuditLogger != null) {
            securityAuditLogger.logTokenEvent("BLACKLIST", username, maskTokenId(tokenId), true);
        }
    }

    /**
     * Blacklist a token (minimal params — backwards-compatible).
     */
    public void blacklistToken(String tokenId, Instant expirationTime) {
        blacklistToken(tokenId, expirationTime, "unknown", "token_rotation", "unknown");
    }

    /**
     * Check if a token is blacklisted.
     * Checks L1 cache first, falls back to DB.
     */
    public boolean isBlacklisted(String tokenId) {
        if (tokenId == null) return false;

        // L1 cache hit — fast path
        if (localCache.containsKey(tokenId)) {
            onBlacklistHit(tokenId);
            return true;
        }

        // L2 — check DB
        Optional<BlacklistedToken> dbEntry = repository.findByTokenId(tokenId);
        if (dbEntry.isPresent()) {
            // Warm the L1 cache
            localCache.put(tokenId, dbEntry.get().getExpiresAt());
            onBlacklistHit(tokenId);

            // Increment reuse counter in DB
            try {
                repository.incrementReuseAttempts(tokenId);
            } catch (Exception e) {
                log.debug("Error incrementing reuse attempts: {}", e.getMessage());
            }
            return true;
        }

        return false;
    }

    private void onBlacklistHit(String tokenId) {
        blacklistHits.incrementAndGet();
        log.warn("Attempted reuse of blacklisted token: {}", maskTokenId(tokenId));

        if (securityAuditLogger != null) {
            securityAuditLogger.logTokenEvent("REUSE_ATTEMPT", "unknown", maskTokenId(tokenId), false);
        }
    }

    /**
     * Clean up expired tokens from both DB and L1 cache every hour.
     * ShedLock ensures only one pod runs the DB purge in multi-instance deployments.
     */
    @Scheduled(fixedRate = 3600000)
    @SchedulerLock(name = "blacklistCleanup", lockAtLeastFor = "30s", lockAtMostFor = "10m")
    public void cleanupBlacklist() {
        Instant now = Instant.now();

        // Purge L1 cache
        int cacheRemoved = 0;
        var iterator = localCache.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().isBefore(now)) {
                iterator.remove();
                cacheRemoved++;
            }
        }

        // Purge DB
        int dbRemoved = 0;
        try {
            dbRemoved = repository.deleteExpiredTokens(now);
        } catch (Exception e) {
            log.warn("Error purging expired blacklisted tokens from DB: {}", e.getMessage());
        }

        if (cacheRemoved > 0 || dbRemoved > 0) {
            log.info("Blacklist cleanup: {} from cache, {} from DB, {} remaining in cache",
                    cacheRemoved, dbRemoved, localCache.size());
        }
    }

    /**
     * Get statistics about the token blacklist.
     */
    public BlacklistStats getStats() {
        return new BlacklistStats(
            localCache.size(),
            totalBlacklistedTokens.get(),
            blacklistHits.get()
        );
    }

    /**
     * Extend the blacklisting period for a token (high-risk scenarios).
     */
    public void extendBlacklisting(String tokenId, long additionalHours) {
        repository.findByTokenId(tokenId).ifPresent(entry -> {
            Instant newExpiration = entry.getExpiresAt().plusSeconds(additionalHours * 3600);
            entry.setExpiresAt(newExpiration);
            repository.save(entry);
            localCache.put(tokenId, newExpiration);
            log.info("Extended blacklisting for token: {}, new expiration: {}",
                    maskTokenId(tokenId), newExpiration);
        });
    }

    /**
     * Mask token ID for logging.
     */
    private String maskTokenId(String tokenId) {
        if (tokenId == null || tokenId.length() < 8) {
            return "***";
        }
        return tokenId.substring(0, 3) + "..." + tokenId.substring(tokenId.length() - 3);
    }

    /**
     * Statistics about the blacklist.
     */
    public static class BlacklistStats {
        private final int currentCacheSize;
        private final int totalBlacklisted;
        private final int blacklistHits;

        public BlacklistStats(int currentCacheSize, int totalBlacklisted, int blacklistHits) {
            this.currentCacheSize = currentCacheSize;
            this.totalBlacklisted = totalBlacklisted;
            this.blacklistHits = blacklistHits;
        }

        public int getCurrentCacheSize() { return currentCacheSize; }
        public int getTotalBlacklisted() { return totalBlacklisted; }
        public int getBlacklistHits() { return blacklistHits; }
    }
}