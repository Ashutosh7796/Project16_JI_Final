package com.spring.jwt.service.impl;

import com.spring.jwt.entity.User;
import com.spring.jwt.exception.BaseException;
import com.spring.jwt.repository.UserRepository;
import com.spring.jwt.utils.DataMaskingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class AccountSecurityService {

    private final UserRepository userRepository;

    public void checkAccountLock(String email) {
        if (isAccountLocked(email)) {
            log.warn("Login attempt on locked account: {}", DataMaskingUtils.maskEmail(email));
            throw new BaseException(String.valueOf(HttpStatus.LOCKED.value()), 
                "Account is temporarily locked due to multiple failed login attempts. Please try again later.");
        }
    }

    public boolean isAccountLocked(String email) {
        User user = userRepository.findByEmail(email);
        if (user == null) {
            return false;
        }
        
        return user.getAccountLockedUntil() != null && 
               LocalDateTime.now().isBefore(user.getAccountLockedUntil());
    }

    @Transactional
    public void lockAccount(String email, int lockDurationMinutes) {
        User user = userRepository.findByEmail(email);
        if (user != null) {
            user.setAccountLocked(true);
            user.setAccountLockedUntil(LocalDateTime.now().plusMinutes(lockDurationMinutes));
            userRepository.save(user);
            log.warn("Account locked for email: {}", DataMaskingUtils.maskEmail(email));
        }
    }

    @Transactional
    public void resetFailedAttempts(String email) {
        User user = userRepository.findByEmail(email);
        if (user != null) {
            user.setFailedLoginAttempts(0);
            user.setAccountLocked(false);
            user.setAccountLockedUntil(null);
            userRepository.save(user);
        }
    }

    @Transactional
    public void incrementFailedAttempts(String email, int maxFailedAttempts) {
        User user = userRepository.findByEmail(email);
        if (user != null) {
            int attempts = user.getFailedLoginAttempts() != null ? user.getFailedLoginAttempts() : 0;
            attempts++;
            user.setFailedLoginAttempts(attempts);
            
            if (attempts >= maxFailedAttempts) {
                user.setAccountLocked(true);
                user.setAccountLockedUntil(LocalDateTime.now().plusMinutes(30));
                log.warn("Account locked for email: {}", DataMaskingUtils.maskEmail(email));
            }
            
            userRepository.save(user);
        }
    }
}