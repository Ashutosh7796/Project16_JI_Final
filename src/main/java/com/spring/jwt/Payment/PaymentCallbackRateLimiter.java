package com.spring.jwt.Payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory sliding-window rate limiter scoped to one JVM. Adequate for single-node deployments
 * and as a per-instance burst guard in front of an external limiter (e.g. CDN/WAF). Counters
 * decay automatically as windows roll forward — no eviction job needed for normal load.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCallbackRateLimiter {

    private final PaymentCallbackSecurityProperties properties;

    private final Map<String, Slot> slots = new ConcurrentHashMap<>();

    public boolean tryAcquire(String key) {
        int max = properties.getRateMaxPerWindow();
        if (max <= 0) {
            return true;
        }
        long windowMillis = Math.max(1L, properties.getRateWindowSeconds()) * 1000L;
        long now = System.currentTimeMillis();
        Slot slot = slots.computeIfAbsent(key, k -> new Slot(now));
        synchronized (slot) {
            long age = now - slot.windowStartMillis;
            if (age >= windowMillis) {
                slot.windowStartMillis = now;
                slot.count.set(0);
            }
            long current = slot.count.incrementAndGet();
            if (current > max) {
                if (current == max + 1L) {
                    log.warn("PAYMENT-CALLBACK-RATE-LIMIT key={} count={} max={} window={}s",
                            key, current, max, properties.getRateWindowSeconds());
                }
                return false;
            }
            return true;
        }
    }

    private static final class Slot {
        long windowStartMillis;
        final AtomicLong count = new AtomicLong(0);

        Slot(long startMillis) {
            this.windowStartMillis = startMillis;
        }
    }
}
