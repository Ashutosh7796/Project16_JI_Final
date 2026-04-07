package com.spring.jwt.Document.Service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service to monitor document processing performance
 */
@Service
@Slf4j
public class PerformanceMonitoringService
{

    private static final long SLOW_OPERATION_THRESHOLD_MS = 1000;

    /**
     * Monitor operation performance and log if slow
     */
    public <T> T monitorOperation(String operationName, java.util.function.Supplier<T> operation)
    {
        long startTime = System.currentTimeMillis();
        
        try {
            T result = operation.get();
            long duration = System.currentTimeMillis() - startTime;
            
            if (duration > SLOW_OPERATION_THRESHOLD_MS)
            {
                log.warn("SLOW OPERATION: {} took {}ms (threshold: {}ms)", 
                        operationName, duration, SLOW_OPERATION_THRESHOLD_MS);
            } else
            {
                log.debug("Operation {} completed in {}ms", operationName, duration);
            }
            
            return result;
        } catch (Exception e)
        {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Operation {} failed after {}ms: {}", operationName, duration, e.getMessage());
            throw e;
        }
    }

    /**
     * Log performance metrics
     */
    public void logPerformanceMetrics(String operation, long durationMs, long fileSizeKB, String processingType)
    {
        double throughputKBps = fileSizeKB / (durationMs / 1000.0);
        
        log.info("PERFORMANCE: {} - Duration: {}ms, File: {}KB, Throughput: {:.2f}KB/s, Type: {}", 
                operation, durationMs, fileSizeKB, throughputKBps, processingType);
        
        if (durationMs > SLOW_OPERATION_THRESHOLD_MS)
        {
            log.warn("PERFORMANCE WARNING: {} exceeded threshold of {}ms", operation, SLOW_OPERATION_THRESHOLD_MS);
        }
    }
}