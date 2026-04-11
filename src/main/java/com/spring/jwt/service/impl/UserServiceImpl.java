package com.spring.jwt.service.impl;

import com.spring.jwt.dto.*;
import com.spring.jwt.entity.*;
import com.spring.jwt.exception.UserNotFoundExceptions;
import com.spring.jwt.repository.UserRepository;
import com.spring.jwt.service.UserService;
import com.spring.jwt.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * User Service Implementation
 * 
 * Handles user management operations:
 * - User CRUD operations
 * - User profile management
 * 
 * Note: Password reset functionality moved to PasswordResetService
 * Note: Registration functionality handled by RegistrationService
 * 
 * @author System
 * @version 2.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<UserDTO> getAllUsers(int pageNo, int pageSize) {
        Pageable pageable = PageRequest.of(pageNo, pageSize);
        Page<User> users = userRepository.findAll(pageable);

        return users.map(user -> {
            UserDTO userDTO = userMapper.toDTO(user);
            return populateRoleSpecificData(user, userDTO);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public UserDTO getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundExceptions("User not found with id: " + id));

        UserDTO userDTO = userMapper.toDTO(user);
        return populateRoleSpecificData(user, userDTO);
    }

    @Override
    @Transactional
    public UserDTO updateUser(Long id, UserUpdateRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundExceptions("User not found with id: " + id));

        if (request.getMobileNumber() != null) {
            user.setMobileNumber(request.getMobileNumber());
        }

        User updatedUser = userRepository.save(user);
        return userMapper.toDTO(updatedUser);
    }

    /**
     * Populates role-specific data in UserDTO
     */
    private UserDTO populateRoleSpecificData(User user, UserDTO userDTO) {
        Set<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        userDTO.setRoles(roles);
        return userDTO;
    }
}
