package com.spring.jwt.config;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class RateLimitingConfig {

    @Value("${app.rate-limiting.enabled:true}")
    private boolean rateLimitingEnabled;

    @Value("${app.rate-limiting.limit-for-period:200}")
    private int limitForPeriod;

    @Value("${app.rate-limiting.refresh-period:600}")
    private int refreshPeriod;

    @Value("${app.rate-limiting.timeout-duration:3000}")
    private int timeoutDuration;

    @Bean
    public RateLimiter authenticationRateLimiter() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(refreshPeriod))
                .limitForPeriod(limitForPeriod)
                .timeoutDuration(Duration.ofSeconds(timeoutDuration))
                .build();
        
        RateLimiterRegistry registry = RateLimiterRegistry.of(config);
        return registry.rateLimiter("authenticationLimiter");
    }
    
    @Bean
    public RateLimiter apiRateLimiter() {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(refreshPeriod))
                .limitForPeriod(limitForPeriod)
                .timeoutDuration(Duration.ofSeconds(timeoutDuration))
                .build();
        
        RateLimiterRegistry registry = RateLimiterRegistry.of(config);
        return registry.rateLimiter("apiLimiter");
    }
} 