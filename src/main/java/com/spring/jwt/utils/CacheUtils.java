package com.spring.jwt.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * Utility class for cache operations across the application.
 * Provides centralized cache management and monitoring capabilities.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CacheUtils {

    private final CacheManager cacheManager;

    /**
     * Cache names used across the application.
     */
    public static class CacheNames {
        // Profile module
        public static final String PROFILES = "profiles";
        public static final String PUBLIC_PROFILES = "publicProfiles";
        public static final String PROFILE_STATS = "profileStats";
        
        // Horoscope module
        public static final String HOROSCOPES = "horoscopes";
        public static final String HOROSCOPE_STATS = "horoscopeStats";
        
        // User module
        public static final String USERS = "users";
        public static final String USER_DETAILS = "userDetails";
        
        // Other modules

        public static final String EXPRESS_INTEREST = "expressInterests";
        public static final String EDUCATION_PROFILES = "educationProfiles";
        public static final String FAMILY_BACKGROUNDS = "familyBackgrounds";
        public static final String PARTNER_PREFERENCES = "partnerPreferences";
        public static final String CONTACT_DETAILS = "contactDetails";
        public static final String COMPLETE_PROFILES = "completeProfiles";
        
        // System caches
        public static final String APPLICATION_SETTINGS = "applicationSettings";
        public static final String LOOKUP_DATA = "lookupData";
    }

    /**
     * Evict a specific cache entry.
     *
     * @param cacheName the cache name
     * @param key       the cache key
     */
    public void evict(String cacheName, Object key) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.evict(key);
                log.debug("Evicted cache entry: cache={}, key={}", cacheName, key);
            }
        } catch (Exception e) {
            log.warn("Failed to evict cache entry: cache={}, key={}, error={}", cacheName, key, e.getMessage());
        }
    }

    /**
     * Clear all entries from a specific cache.
     *
     * @param cacheName the cache name
     */
    public void clear(String cacheName) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
                log.info("Cleared cache: {}", cacheName);
            }
        } catch (Exception e) {
            log.warn("Failed to clear cache: cache={}, error={}", cacheName, e.getMessage());
        }
    }

    /**
     * Clear all caches in the application.
     */
    public void clearAll() {
        try {
            cacheManager.getCacheNames().forEach(this::clear);
            log.info("Cleared all application caches");
        } catch (Exception e) {
            log.error("Failed to clear all caches: {}", e.getMessage());
        }
    }

    /**
     * Get cache statistics (if supported by the cache implementation).
     *
     * @param cacheName the cache name
     * @return cache statistics as string
     */
    public String getCacheStats(String cacheName) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                return String.format("Cache: %s, Native Cache: %s", cacheName, cache.getNativeCache().getClass().getSimpleName());
            }
            return "Cache not found: " + cacheName;
        } catch (Exception e) {
            return "Error getting cache stats: " + e.getMessage();
        }
    }

    /**
     * Check if a cache exists.
     *
     * @param cacheName the cache name
     * @return true if cache exists
     */
    public boolean cacheExists(String cacheName) {
        return cacheManager.getCache(cacheName) != null;
    }
}