package com.spring.jwt.service;

import com.spring.jwt.dto.ResetPassword;
import com.spring.jwt.utils.ResponseDto;

/**
 * Password Reset Service Interface
 * 
 * Handles all password reset operations with security best practices
 */
public interface PasswordResetService {

    /**
     * Initiates password reset process by sending email with reset token
     * 
     * @param email User's email address
     * @return Response indicating success or failure
     */
    ResponseDto initiatePasswordReset(String email);

    /**
     * Validates if reset token is valid and not expired
     * 
     * @param token Reset token
     * @return true if valid, false otherwise
     */
    boolean validateResetToken(String token);

    /**
     * Resets user password using valid token
     * 
     * @param request Reset password request with token and new password
     * @return Response indicating success or failure
     */
    ResponseDto resetPassword(ResetPassword request);
}
