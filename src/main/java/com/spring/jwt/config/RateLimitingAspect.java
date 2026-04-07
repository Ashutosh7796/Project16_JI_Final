package com.spring.jwt.config;

import com.spring.jwt.exception.BaseException;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class RateLimitingAspect {

    private final RateLimiter authenticationRateLimiter;
    private final RateLimiter apiRateLimiter;

    @Value("${app.rate-limiting.enabled:true}")
    private boolean rateLimitingEnabled;

    @Around("execution(* com.spring.jwt.config.filter.JwtUsernamePasswordAuthenticationFilter.attemptAuthentication(..))")
    public Object limitAuthentication(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!rateLimitingEnabled) {
            return joinPoint.proceed();
        }

        String clientIp = getClientIp();
        String key = "auth:" + clientIp;
        log.debug("Rate limiting authentication request from IP: {}", clientIp);

        try {
            return authenticationRateLimiter.executeCheckedSupplier(() -> {
                try {
                    return joinPoint.proceed();
                } catch (Throwable throwable) {
                    if (throwable instanceof RuntimeException) {
                        throw (RuntimeException) throwable;
                    }
                    throw new RuntimeException(throwable);
                }
            });
        } catch (RequestNotPermitted e) {
            log.warn("Rate limit exceeded for authentication request from IP: {}", clientIp);
            throw new BaseException(String.valueOf(HttpStatus.TOO_MANY_REQUESTS.value()), 
                    "Too many authentication attempts. Please try again later.");
        }
    }

    @Around("execution(* com.spring.jwt.service.UserService.handleForgotPassword(..)) || " +
            "execution(* com.spring.jwt.service.UserService.processPasswordUpdate(..))")
    public Object limitSensitiveOperations(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!rateLimitingEnabled) {
            return joinPoint.proceed();
        }

        String clientIp = getClientIp();
        log.debug("Rate limiting sensitive operation from IP: {}", clientIp);

        try {
            return authenticationRateLimiter.executeCheckedSupplier(() -> {
                try {
                    return joinPoint.proceed();
                } catch (Throwable throwable) {
                    if (throwable instanceof RuntimeException) {
                        throw (RuntimeException) throwable;
                    }
                    throw new RuntimeException(throwable);
                }
            });
        } catch (RequestNotPermitted e) {
            log.warn("Rate limit exceeded for sensitive operation from IP: {}", clientIp);
            throw new BaseException(String.valueOf(HttpStatus.TOO_MANY_REQUESTS.value()), 
                    "Too many requests. Please try again later.");
        }
    }

    @Around("execution(* com.spring.jwt.controller.*.*(..)) && " +
            "!execution(* com.spring.jwt.controller.*.handleForgotPassword(..)) && " +
            "!execution(* com.spring.jwt.controller.*.updatePassword(..))")
    public Object limitApiRequests(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!rateLimitingEnabled) {
            return joinPoint.proceed();
        }

        String clientIp = getClientIp();
        log.debug("Rate limiting API request from IP: {}", clientIp);

        try {
            return apiRateLimiter.executeCheckedSupplier(() -> {
                try {
                    return joinPoint.proceed();
                } catch (Throwable throwable) {
                    if (throwable instanceof RuntimeException) {
                        throw (RuntimeException) throwable;
                    }
                    throw new RuntimeException(throwable);
                }
            });
        } catch (RequestNotPermitted e) {
            log.warn("Rate limit exceeded for API request from IP: {}", clientIp);
            throw new BaseException(String.valueOf(HttpStatus.TOO_MANY_REQUESTS.value()), 
                    "Too many requests. Please try again later.");
        }
    }

    private String getClientIp() {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                .getRequest();
        
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        return request.getRemoteAddr();
    }
} 