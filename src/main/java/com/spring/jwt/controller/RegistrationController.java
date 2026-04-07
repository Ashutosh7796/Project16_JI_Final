package com.spring.jwt.controller;

import com.spring.jwt.dto.*;
import com.spring.jwt.service.SecureAuthenticationService;
import com.spring.jwt.service.UserService;
import com.spring.jwt.utils.BaseResponseDTO;
import com.spring.jwt.utils.ErrorResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(
    name = "Secure Authentication API",
    description = "Secure authentication endpoints with device fingerprinting and role-based access control"
)
@RestController
@RequestMapping("/api/auth/v1")
@RequiredArgsConstructor
@Validated
@Slf4j
@CrossOrigin(origins = "${app.cors.allowed-origins}", maxAge = 3600)
public class RegistrationController {

    private final SecureAuthenticationService secureAuthenticationService;
    private final UserService userService;

///////////////////////////////////////////////////////////////////////////////////
//
//      File Name    : UserController
//      Description  : registerUser
//      Author       : Ashutosh Shedge
//      Date         : 24/12/2025
//
//////////////////////////////////////////////////////////////////////////////////

    @Operation(
        summary = "Register a new user account",
        description = "Creates a new user account with enhanced security validations. Role is automatically set to USER.",
        tags = {"User Registration"}
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "User account created successfully",
            content = @Content(schema = @Schema(implementation = BaseResponseDTO.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid input data or account already exists",
            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))
        )
    })
    @PostMapping("/register")
    public ResponseEntity<BaseResponseDTO> registerUser(
            @Valid @RequestBody SecureUserRegistrationRequest request,
            HttpServletRequest httpRequest) {
        
        log.info("User registration request received");
        BaseResponseDTO response = secureAuthenticationService.registerUser(request, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
        summary = "Register a new admin account (Restricted Access)",
        description = "Creates a new admin account with restricted access. Requires valid admin secret key.",
        tags = {"Admin Registration"},
        security = {@SecurityRequirement(name = "admin-secret")}
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "Admin account created successfully",
            content = @Content(schema = @Schema(implementation = BaseResponseDTO.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid input data or account already exists",
            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))
        ),
        @ApiResponse(
            responseCode = "403",
            description = "Invalid admin secret key",
            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))
        )
    })
    @PostMapping("/admin/register")
    public ResponseEntity<BaseResponseDTO> registerAdmin(
            @Valid @RequestBody AdminRegistrationRequest request,
            HttpServletRequest httpRequest) {
        
        log.info("Admin registration request received");
        BaseResponseDTO response = secureAuthenticationService.registerAdmin(request, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
        summary = "Authenticate user with device fingerprinting",
        description = "Authenticates user and creates device fingerprint for enhanced security",
        tags = {"Authentication"}
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Authentication successful",
            content = @Content(schema = @Schema(implementation = BaseResponseDTO.class))
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Invalid credentials",
            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))
        ),
        @ApiResponse(
            responseCode = "423",
            description = "Account locked due to multiple failed attempts",
            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))
        )
    })
    @PostMapping("/authenticate")
    public ResponseEntity<BaseResponseDTO> authenticateUser(
            @Valid @RequestBody SecureLoginRequest request,
            HttpServletRequest httpRequest) {
        
        log.info("User authentication request received");
        BaseResponseDTO response = secureAuthenticationService.authenticateUser(request, httpRequest);
        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Check account lock status",
        description = "Checks if a user account is currently locked",
        tags = {"Security"}
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Account status retrieved successfully"
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid email format",
            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))
        )
    })
    @GetMapping("/account-status")
    public ResponseEntity<BaseResponseDTO> checkAccountStatus(
            @RequestParam String email) {
        
        boolean isLocked = secureAuthenticationService.isAccountLocked(email);
        
        BaseResponseDTO response = new BaseResponseDTO();
        response.setCode(String.valueOf(HttpStatus.OK.value()));
        response.setMessage(isLocked ? "Account is locked" : "Account is active");
        
        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Unlock user account (Admin Only)",
        description = "Unlocks a user account and resets failed login attempts",
        tags = {"Admin Operations"},
        security = {@SecurityRequirement(name = "bearer-jwt")}
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Account unlocked successfully"
        ),
        @ApiResponse(
            responseCode = "403",
            description = "Insufficient permissions",
            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))
        )
    })
    @PostMapping("/admin/unlock-account")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BaseResponseDTO> unlockAccount(
            @RequestParam String email) {
        
        log.info("Admin unlock account request for email: {}", email);
        secureAuthenticationService.resetFailedLoginAttempts(email);
        
        BaseResponseDTO response = new BaseResponseDTO();
        response.setCode(String.valueOf(HttpStatus.OK.value()));
        response.setMessage("Account unlocked successfully");
        
        return ResponseEntity.ok(response);
    }

    @Operation(
        summary = "Validate admin secret key",
        description = "Validates if the provided admin secret key is correct",
        tags = {"Admin Operations"}
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Secret key validation result"
        )
    })
    @PostMapping("/admin/validate-secret")
    public ResponseEntity<BaseResponseDTO> validateAdminSecret(
            @RequestParam String secretKey) {
        
        boolean isValid = secureAuthenticationService.validateAdminSecretKey(secretKey);
        
        BaseResponseDTO response = new BaseResponseDTO();
        response.setCode(String.valueOf(HttpStatus.OK.value()));
        response.setMessage(isValid ? "Valid admin secret key" : "Invalid admin secret key");
        
        return ResponseEntity.ok(response);
    }

//    @Operation(
//            summary = "Register a new user account",
//            description = "Creates a new user account with the provided user details",
//            tags = {"Authentication"}
//    )
//    @ApiResponses({
//            @ApiResponse(
//                    responseCode = "201",
//                    description = "User account created successfully"
//            ),
//            @ApiResponse(
//                    responseCode = "400",
//                    description = "Invalid input or account already exists",
//                    content = @Content(
//                            schema = @Schema(implementation = ErrorResponseDto.class)
//                    )
//            ),
//            @ApiResponse(
//                    responseCode = "500",
//                    description = "Internal server error",
//                    content = @Content(
//                            schema = @Schema(implementation = ErrorResponseDto.class)
//                    )
//            )
//    })
//    @PostMapping("/register")
//    public ResponseEntity<BaseResponseDTO> registerUser(@Valid @RequestBody UserDTO userDTO) {
//        BaseResponseDTO response = userService.registerAccount(userDTO);
//        return ResponseEntity.status(HttpStatus.CREATED).body(response);
//    }

}