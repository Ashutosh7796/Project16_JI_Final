package com.spring.jwt.config.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Aspect
@Component
@Slf4j
public class LoggingAspect {

    private static final String CORRELATION_ID_KEY = "correlationId";

    @Around("execution(* com.spring.jwt.profile..*(..))")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        boolean isCorrelationIdGeneratedHere = false;
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();

        String correlationId = MDC.get(CORRELATION_ID_KEY);
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
            MDC.put(CORRELATION_ID_KEY, correlationId);
        }

        log.info("[{}] Entering {}.{}", correlationId, className, methodName);

        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long executionTime = System.currentTimeMillis() - start;

            log.info("[{}] Exiting {}.{} - Duration: {} ms", correlationId, className, methodName, executionTime);
            return result;
        } catch (Throwable e) {
            long executionTime = System.currentTimeMillis() - start;
            log.error("[{}] Exception in {}.{} - Duration: {} ms - Error: {}",
                    correlationId, className, methodName, executionTime, e.getMessage());
            throw e;
        } finally {
            if (isCorrelationIdGeneratedHere)
            {
                MDC.remove(CORRELATION_ID_KEY);
            }
        }
    }
}
