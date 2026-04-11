package com.spring.jwt.service.impl;

import com.spring.jwt.dto.ResetPassword;
import com.spring.jwt.entity.User;
import com.spring.jwt.exception.UserNotFoundExceptions;
import com.spring.jwt.repository.UserRepository;
import com.spring.jwt.service.PasswordResetService;
import com.spring.jwt.utils.DataMaskingUtils;
import com.spring.jwt.utils.EmailService;
import com.spring.jwt.utils.ResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Password Reset Service Implementation
 * 
 * Industrial-standard implementation with:
 * - Secure token generation (64 characters alphanumeric)
 * - Token expiry (30 minutes)
 * - Password validation
 * - Audit logging
 * - Transaction management
 * - Error handling
 * 
 * @author System
 * @version 2.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetServiceImpl implements PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${app.url.password-reset:http://localhost:3000/reset-password}")
    private String passwordResetUrl;

    @Value("${app.password-reset.token-expiry-minutes:30}")
    private int tokenExpiryMinutes;

    @Override
    @Transactional
    public ResponseDto initiatePasswordReset(String email) {
        // Validate input
        if (email == null || email.trim().isEmpty()) {
            log.warn("Password reset attempt with empty email");
            return new ResponseDto("Unsuccessful", "Email is required");
        }

        // Find user
        User user = userRepository.findByEmail(email.trim().toLowerCase());
        if (user == null) {
            // Security: Don't reveal if email exists
            log.warn("Password reset attempt for non-existent email: {}", 
                DataMaskingUtils.maskEmail(email));
            // Return success to prevent email enumeration
            return new ResponseDto("Successful", 
                "If an account exists with this email, you will receive password reset instructions");
        }

        // Check if account is locked
        if (Boolean.TRUE.equals(user.getAccountLocked())) {
            log.warn("Password reset attempt for locked account: {}", 
                DataMaskingUtils.maskEmail(email));
            return new ResponseDto("Unsuccessful", 
                "Account is locked. Please contact support.");
        }

        // Generate secure token
        String token = generateSecureToken();
        
        // Save token with expiry
        user.setResetPasswordToken(token);
        user.setResetPasswordTokenExpiry(LocalDateTime.now().plusMinutes(tokenExpiryMinutes));
        userRepository.save(user);
        
        log.info("Password reset token generated for user: {}", 
            DataMaskingUtils.maskEmail(email));

        // Send email
        String resetLink = passwordResetUrl + "?token=" + token;
        try {
            emailService.sendResetPasswordEmail(email, resetLink);
            log.info("Password reset email sent to: {}", 
                DataMaskingUtils.maskEmail(email));
            return new ResponseDto("Successful", 
                "Password reset instructions sent to your email");
        } catch (Exception e) {
            log.error("Failed to send password reset email to: {}", 
                DataMaskingUtils.maskEmail(email), e);
            // Rollback token if email fails
            user.setResetPasswordToken(null);
            user.setResetPasswordTokenExpiry(null);
            userRepository.save(user);
            return new ResponseDto("Unsuccessful", 
                "Failed to send reset instructions. Please try again later.");
        }
    }

    @Override
    public boolean validateResetToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }

        User user = userRepository.findByResetPasswordToken(token);
        if (user == null) {
            log.debug("Reset token not found in database");
            return false;
        }

        boolean isValid = user.getResetPasswordTokenExpiry() != null &&
                LocalDateTime.now().isBefore(user.getResetPasswordTokenExpiry());

        if (!isValid) {
            log.debug("Expired reset token used for user: {}", 
                DataMaskingUtils.maskEmail(user.getEmail()));
        }

        return isValid;
    }

    @Override
    @Transactional
    public ResponseDto resetPassword(ResetPassword request) {
        // Validate input
        if (request.getPassword() == null || request.getConfirmPassword() == null || 
            request.getToken() == null) {
            log.warn("Password reset attempt with missing fields");
            return new ResponseDto("Unsuccessful", "All fields are required");
        }

        // Validate password match
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            log.warn("Password mismatch in reset request");
            return new ResponseDto("Unsuccessful", "Passwords do not match");
        }

        // Validate token
        if (!validateResetToken(request.getToken())) {
            log.warn("Invalid or expired token used in password reset");
            return new ResponseDto("Unsuccessful", "Invalid or expired token");
        }

        // Find user by token
        User user = userRepository.findByResetPasswordToken(request.getToken());
        if (user == null) {
            log.error("User not found for valid token - data inconsistency");
            return new ResponseDto("Unsuccessful", "Invalid token");
        }

        // Check if new password is same as old password
        if (passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("User attempted to reuse old password: {}", 
                DataMaskingUtils.maskEmail(user.getEmail()));
            return new ResponseDto("Unsuccessful", 
                "New password cannot be the same as your current password");
        }

        // Update password
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setResetPasswordToken(null);
        user.setResetPasswordTokenExpiry(null);
        
        // Reset failed login attempts on successful password reset
        user.setFailedLoginAttempts(0);
        user.setAccountLocked(false);
        user.setAccountLockedUntil(null);
        
        userRepository.save(user);
        
        log.info("Password successfully reset for user: {}", 
            DataMaskingUtils.maskEmail(user.getEmail()));

        return new ResponseDto("Successful", "Password reset successful. You can now login with your new password.");
    }

    /**
     * Generates a cryptographically secure random token
     * 
     * @return 64-character alphanumeric token
     */
    private String generateSecureToken() {
        return RandomStringUtils.randomAlphanumeric(64);
    }
}
