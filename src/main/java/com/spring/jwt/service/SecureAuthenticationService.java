package com.spring.jwt.service;

import com.spring.jwt.dto.*;
import com.spring.jwt.utils.BaseResponseDTO;
import jakarta.servlet.http.HttpServletRequest;

public interface SecureAuthenticationService {
    
    /**
     * Register a new user with security validations
     */
    BaseResponseDTO registerUser(SecureUserRegistrationRequest request, HttpServletRequest httpRequest);
    
    /**
     * Register a new admin with restricted access
     */
    BaseResponseDTO registerAdmin(AdminRegistrationRequest request, HttpServletRequest httpRequest);
    
    /**
     * Authenticate user with device fingerprinting
     */
    BaseResponseDTO authenticateUser(SecureLoginRequest request, HttpServletRequest httpRequest);
    
    /**
     * Validate admin secret key
     */
    boolean validateAdminSecretKey(String secretKey);
    
    /**
     * Generate device fingerprint from request
     */
    String generateDeviceFingerprint(HttpServletRequest request, DeviceInfo deviceInfo);
    
    /**
     * Check if account is locked
     */
    boolean isAccountLocked(String email);
    
    /**
     * Lock account after failed attempts
     */
    void lockAccount(String email);
    
    /**
     * Reset failed login attempts
     */
    void resetFailedLoginAttempts(String email);
    
    /**
     * Increment failed login attempts
     */
    void incrementFailedLoginAttempts(String email);
}