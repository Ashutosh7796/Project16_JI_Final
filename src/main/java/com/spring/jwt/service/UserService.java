package com.spring.jwt.service;

import com.spring.jwt.dto.UserDTO;
import com.spring.jwt.dto.UserUpdateRequest;
import org.springframework.data.domain.Page;

/**
 * User Service Interface
 * 
 * Handles user management operations
 * 
 * Note: Password reset moved to PasswordResetService
 * Note: Registration moved to RegistrationService
 */
public interface UserService {
    
    /**
     * Get all users with pagination
     */
    Page<UserDTO> getAllUsers(int pageNo, int pageSize);

    /**
     * Get user by ID
     */
    UserDTO getUserById(Long id);

    /**
     * Update user information
     */
    UserDTO updateUser(Long id, UserUpdateRequest request);
}
