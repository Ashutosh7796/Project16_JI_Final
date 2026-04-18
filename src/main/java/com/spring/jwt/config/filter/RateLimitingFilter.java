package com.spring.jwt.config.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Filter to prevent brute force attacks by implementing rate limiting.
 * <p>
 * Evicts stale entries every 5 minutes and caps map at {@code MAX_TRACKED_IPS}
 * to prevent OOM under bot/scanner attacks.
 */
@Component
@Slf4j
public class RateLimitingFilter implements Filter, Ordered {

    private static final int STATUS_TOO_MANY_REQUESTS = 429;
    /** Hard cap — if exceeded, oldest entries evicted on next cleanup. */
    private static final int MAX_TRACKED_IPS = 50_000;

    private final Map<String, RequestCounter> requestCounts = new ConcurrentHashMap<>();
    
    @Value("${app.rate-limiting.enabled:true}")
    private boolean rateLimitingEnabled;

    @Value("${app.rate-limiting.limit-for-period:20}")
    private int limitForPeriod;

    @Value("${app.rate-limiting.refresh-period:60}")
    private int refreshPeriod;
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        
        if (!rateLimitingEnabled) {
            chain.doFilter(request, response);
            return;
        }

        String path = httpRequest.getRequestURI();
        if (isPublicEndpoint(path)) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(httpRequest);

        if (isRateLimitExceeded(clientIp)) {
            httpResponse.setStatus(STATUS_TOO_MANY_REQUESTS);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("{\"error\":\"Rate limit exceeded. Please try again later.\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    @Override
    public void init(FilterConfig filterConfig) {

    }

    @Override
    public void destroy() {

    }
    
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 30;
    }

    /**
     * Evict stale entries every 5 minutes to prevent unbounded memory growth.
     * Also enforces MAX_TRACKED_IPS hard cap.
     */
    @Scheduled(fixedRate = 300_000)
    public void evictStaleEntries() {
        long now = System.currentTimeMillis();
        long windowMs = TimeUnit.SECONDS.toMillis(refreshPeriod);
        int before = requestCounts.size();

        // Remove entries whose window has expired (no recent requests)
        requestCounts.entrySet().removeIf(e ->
                now - e.getValue().getWindowStart() > windowMs * 2);

        // Hard cap: if still too many, remove oldest entries
        if (requestCounts.size() > MAX_TRACKED_IPS) {
            int toRemove = requestCounts.size() - MAX_TRACKED_IPS;
            requestCounts.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue(
                            (a, b) -> Long.compare(a.getWindowStart(), b.getWindowStart())))
                    .limit(toRemove)
                    .map(Map.Entry::getKey)
                    .toList()
                    .forEach(requestCounts::remove);
        }

        int evicted = before - requestCounts.size();
        if (evicted > 0) {
            log.debug("Rate limiter evicted {} stale entries, {} remaining", evicted, requestCounts.size());
        }
    }
    
    /**
     * Checks if the request from the given IP exceeds the rate limit
     */
    private boolean isRateLimitExceeded(String clientIp) {
        long now = System.currentTimeMillis();

        RequestCounter counter = requestCounts.computeIfAbsent(clientIp, k -> new RequestCounter());

        if (now - counter.getWindowStart() > TimeUnit.SECONDS.toMillis(refreshPeriod)) {
            counter.reset(now);
        }

        counter.incrementCount();

        return counter.getCount() > limitForPeriod;
    }
    
    /**
     * Gets the client IP address from the request
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
    
    /**
     * Checks if the given path is a public endpoint that should not be rate limited
     */
    private boolean isPublicEndpoint(String path) {
        return path.startsWith("/api/public/") ||
                path.startsWith("/swagger-ui") ||
                path.startsWith("/v3/api-docs") ||
                path.startsWith("/h2-console") ||
                path.startsWith("/actuator/") ||
                // CCAvenue posts form-encoded bodies; shared egress IPs must not trip global IP limits
                path.startsWith("/api/payment/product/") ||
                path.startsWith("/api/payment/farmer/") ||
                path.equals("/api/payment/queue/status") ||
                path.equals("/api/auth/login") ||
                path.equals("/api/auth/register") ||
                path.equals("/api/auth/refresh") ||
                // Actual app routes (JwtConfig defaults + RegistrationController)
                path.equals("/jwt/login") ||
                path.equals("/jwt/refresh") ||
                path.startsWith("/api/auth/v1/register");
    }
    
    /**
     * Class to track request counts in a time window
     */
    private static class RequestCounter {
        private long windowStart;
        private int count;
        
        public RequestCounter() {
            this.windowStart = System.currentTimeMillis();
            this.count = 0;
        }
        
        public void reset(long timestamp) {
            this.windowStart = timestamp;
            this.count = 0;
        }
        
        public void incrementCount() {
            this.count++;
        }
        
        public int getCount() {
            return count;
        }
        
        public long getWindowStart() {
            return windowStart;
        }
    }
}