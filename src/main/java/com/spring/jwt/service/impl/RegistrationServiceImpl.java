package com.spring.jwt.service.impl;

import com.spring.jwt.dto.*;
import com.spring.jwt.entity.Role;
import com.spring.jwt.entity.User;
import com.spring.jwt.exception.BaseException;
import com.spring.jwt.repository.RoleRepository;
import com.spring.jwt.repository.UserRepository;
import com.spring.jwt.service.SecureAuthenticationService;
import com.spring.jwt.utils.BaseResponseDTO;
import com.spring.jwt.utils.DataMaskingUtils;
import com.spring.jwt.utils.EncryptionUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationServiceImpl implements SecureAuthenticationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final UserFactory userFactory;
    private final ValidationService validationService;
    private final AccountSecurityService accountSecurityService;
    private final DeviceFingerprintService deviceFingerprintService;

    @Value("${app.admin.secret-key}")
    private String adminSecretKey;

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int ACCOUNT_LOCK_DURATION_MINUTES = 30;

//    @Override
//    @Transactional
//    public BaseResponseDTO registerUser(SecureUserRegistrationRequest request, HttpServletRequest httpRequest)
//    {
//        log.info("Starting user registration for email: {}", DataMaskingUtils.maskEmail(request.getEmail()));
//
//        return executeRegistration(
//            () -> {
//                validationService.validateUserRegistration(request);
//                checkEmailAndMobileAvailability(request.getEmail(), request.getMobileNumber());
//                return userFactory.createUser(request, httpRequest);
//            },
//            "USER",
//            "User registered successfully"
//        );
//    }
@Override
@Transactional
public BaseResponseDTO registerUser(
        SecureUserRegistrationRequest request,
        HttpServletRequest httpRequest) {

    return executeRegistration1(
            () -> {
                validationService.validateUserRegistration(request);
                checkEmailAndMobileAvailability(
                        request.getEmail(),
                        request.getMobileNumber()
                );
                return userFactory.createUser(request, httpRequest);
            },
            "User registered successfully"
    );
}

    @Override
    @Transactional
    public BaseResponseDTO registerAdmin(AdminRegistrationRequest request, HttpServletRequest httpRequest)
    {
        log.info("Starting admin registration for email: {}", DataMaskingUtils.maskEmail(request.getEmail()));

        if (!validateAdminSecretKey(request.getAdminSecretKey())) {
            log.warn("Invalid admin secret key provided for registration");
            throw new BaseException(String.valueOf(HttpStatus.FORBIDDEN.value()), "Invalid admin secret key");
        }

        return executeRegistration(
            () -> {
                validationService.validateAdminRegistration(request);
                checkEmailAvailability(request.getEmail());
                return userFactory.createAdmin(request, httpRequest);
            },
            "ADMIN",
            "Admin registered successfully"
        );
    }

    @Override
    public BaseResponseDTO authenticateUser(SecureLoginRequest request, HttpServletRequest httpRequest)
    {
        log.info("Authentication attempt for email: {}", DataMaskingUtils.maskEmail(request.getEmail()));

        accountSecurityService.checkAccountLock(request.getEmail());
        
        User user = authenticateCredentials(request);
        
        accountSecurityService.resetFailedAttempts(user.getEmail());
        updateUserLoginInfo(user, httpRequest, request.getDeviceInfo());
        
        log.info("User authenticated successfully: {}", DataMaskingUtils.maskEmail(request.getEmail()));
        
        return createSuccessResponse(HttpStatus.OK, "Authentication successful", user.getUserId());
    }

    @Override
    public boolean validateAdminSecretKey(String secretKey)
    {
        return adminSecretKey.equals(secretKey);
    }

    @Override
    public String generateDeviceFingerprint(HttpServletRequest request, DeviceInfo deviceInfo)
    {
        return deviceFingerprintService.generateFingerprint(request, deviceInfo);
    }

    @Override
    public boolean isAccountLocked(String email)
    {
        return accountSecurityService.isAccountLocked(email);
    }

    @Override
    @Transactional
    public void lockAccount(String email)
    {
        accountSecurityService.lockAccount(email, ACCOUNT_LOCK_DURATION_MINUTES);
    }

    @Override
    @Transactional
    public void resetFailedLoginAttempts(String email)
    {
        accountSecurityService.resetFailedAttempts(email);
    }

    @Override
    @Transactional
    public void incrementFailedLoginAttempts(String email)
    {
        accountSecurityService.incrementFailedAttempts(email, MAX_FAILED_ATTEMPTS);
    }

    private BaseResponseDTO executeRegistration(Supplier<User> userCreator, String roleName, String successMessage)
    {
        try {
            User user = userCreator.get();
//            assignRole(user, roleName);
            User savedUser = userRepository.save(user);

            log.info("{} registered successfully with ID: {}", roleName.toLowerCase(), savedUser.getUserId());

            return createSuccessResponse(HttpStatus.CREATED, successMessage, Long.valueOf(savedUser.getUserId()));

        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error during {} registration: {}", roleName.toLowerCase(), e.getMessage(), e);
            throw new BaseException(String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()),
                "Registration failed. Please try again.");
        }
    }
    private BaseResponseDTO executeRegistration1(
            Supplier<User> userCreator,
            String successMessage) {

        User savedUser = userCreator.get(); // already saved inside factory

        log.info("User registered successfully with ID: {}", savedUser.getUserId());

        return createSuccessResponse(
                HttpStatus.CREATED,
                successMessage,
                savedUser.getUserId()
        );
    }


    private void checkEmailAndMobileAvailability(String email, Long mobileNumber) {
        checkEmailAvailability(email);
        
        if (userRepository.findByMobileNumber(mobileNumber).isPresent()) {
            log.warn("Registration attempt with existing mobile number");
            throw new BaseException(String.valueOf(HttpStatus.BAD_REQUEST.value()), 
                "Mobile number is already registered");
        }
    }

    private void checkEmailAvailability(String email) {
        if (userRepository.findByEmail(email) != null) {
            log.warn("Registration attempt with existing email: {}", DataMaskingUtils.maskEmail(email));
            throw new BaseException(String.valueOf(HttpStatus.BAD_REQUEST.value()), 
                "Email is already registered");
        }
    }

    private void assignRole(User user, String roleName) {
        Role role = roleRepository.findByName(roleName);
        if (role == null) {
            throw new BaseException(String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()), 
                roleName + " role not found");
        }
        
        Set<Role> roles = new HashSet<>();
        roles.add(role);
        user.setRoles(roles);
    }

    private User authenticateCredentials(SecureLoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail());
        
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            accountSecurityService.incrementFailedAttempts(request.getEmail(), MAX_FAILED_ATTEMPTS);
            log.warn("Invalid login credentials for email: {}", DataMaskingUtils.maskEmail(request.getEmail()));
            throw new BaseException(String.valueOf(HttpStatus.UNAUTHORIZED.value()), 
                "Invalid email or password");
        }
        
        return user;
    }

    private void updateUserLoginInfo(User user, HttpServletRequest httpRequest, DeviceInfo deviceInfo) {
        String deviceFingerprint = deviceFingerprintService.generateFingerprint(httpRequest, deviceInfo);
        user.setDeviceFingerprint(deviceFingerprint);
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);
    }

    private BaseResponseDTO createSuccessResponse(HttpStatus status, String message, Long userId) {
        BaseResponseDTO response = new BaseResponseDTO();
        response.setCode(String.valueOf(status.value()));
        response.setMessage(message);
        response.setUserID(userId);
        return response;
    }
}