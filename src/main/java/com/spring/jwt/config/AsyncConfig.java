package com.spring.jwt.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Dedicated pool for payment callbacks; separate default pool for {@code @Async} without a qualifier.
 */
@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig {

    @Bean(name = "applicationTaskExecutor")
    public ThreadPoolTaskExecutor applicationTaskExecutor(
            @Value("${app.async.default.core-pool-size:2}") int corePoolSize,
            @Value("${app.async.default.max-pool-size:4}") int maxPoolSize,
            @Value("${app.async.default.queue-capacity:50}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("async-app-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        log.info("Default async executor: core={}, max={}, queue={}", corePoolSize, maxPoolSize, queueCapacity);
        return executor;
    }

    @Bean(name = "paymentCallbackExecutor")
    public ThreadPoolTaskExecutor paymentCallbackExecutor(
            @Value("${payment.callback.async.core-pool-size:5}") int corePoolSize,
            @Value("${payment.callback.async.max-pool-size:10}") int maxPoolSize,
            @Value("${payment.callback.async.queue-capacity:100}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("payment-callback-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        log.info("Payment callback async executor: core={}, max={}, queue={}", corePoolSize, maxPoolSize, queueCapacity);
        return executor;
    }

    @Bean(name = "documentProcessingExecutor")
    public ThreadPoolTaskExecutor documentProcessingExecutor(
            @Value("${document.async.core-pool-size:3}") int corePoolSize,
            @Value("${document.async.max-pool-size:6}") int maxPoolSize,
            @Value("${document.async.queue-capacity:50}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("doc-process-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        log.info("Document processing async executor: core={}, max={}, queue={}", corePoolSize, maxPoolSize, queueCapacity);
        return executor;
    }

    @Bean(name = "dashboardStatsExecutor")
    public ThreadPoolTaskExecutor dashboardStatsExecutor(
            @Value("${dashboard.async.core-pool-size:4}") int corePoolSize,
            @Value("${dashboard.async.max-pool-size:8}") int maxPoolSize,
            @Value("${dashboard.async.queue-capacity:32}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("dash-stats-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        log.info("Dashboard stats executor: core={}, max={}, queue={}", corePoolSize, maxPoolSize, queueCapacity);
        return executor;
    }

    @Bean
    public AsyncConfigurer asyncConfigurer(
            @Qualifier("applicationTaskExecutor") Executor applicationTaskExecutor) {
        return new AsyncConfigurer() {
            @Override
            public Executor getAsyncExecutor() {
                return applicationTaskExecutor;
            }

            @Override
            public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
                return (ex, method, params) ->
                        log.error("Async execution error in method: {}, params: {}", method.getName(), params, ex);
            }
        };
    }
}
