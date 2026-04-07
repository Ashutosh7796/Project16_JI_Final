package com.spring.jwt.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheResolver;
import org.springframework.cache.interceptor.SimpleKeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.util.Arrays;

/**
 * Centralized cache configuration for the entire application.
 * Configures caching for all modules with a single cache manager.
 * 
 * Supports different cache implementations based on active profiles:
 * - Development: ConcurrentMapCacheManager (in-memory)
 * - Production: Can be extended to use Redis or other distributed cache solutions
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    @Value("${app.cache.enabled:true}")
    private boolean cacheEnabled;

    /**
     * Primary cache manager for the entire application.
     * Supports multiple cache regions for different data types.
     */
    @Bean
    @Primary
    @Profile("!prod")
    public CacheManager cacheManager() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();

        cacheManager.setCacheNames(Arrays.asList(
            "profiles",
            "publicProfiles", 
            "profileStats",
            "public_profiles",
            "horoscopes",
            "horoscopeStats",
            "users",
            "userDetails",
            "userSessions",
            "educationProfiles",
            "familyBackgrounds",
            "partnerPreferences",
            "contactDetails",
            "documents",
            "documentMetadata",
            "completeProfiles",
            "expressInterests",
            "subscriptions",
            "userCredits",
            "applicationSettings",
            "lookupData",
            "systemConfig"
        ));
        cacheManager.setAllowNullValues(false);
        
        return cacheManager;
    }

    /**
     * Production cache manager - placeholder for Redis or other distributed cache.
     * Uncomment and configure when moving to production with distributed caching.
     */
    /*
    @Bean
    @Primary
    @Profile("prod")
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(30))
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));
            
        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(config)
            .build();
    }
    */

    @Override
    public CacheResolver cacheResolver() {
        return new SimpleCacheResolver(cacheManager());
    }

    @Override
    public KeyGenerator keyGenerator() {
        return new SimpleKeyGenerator();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new SimpleCacheErrorHandler();
    }
}