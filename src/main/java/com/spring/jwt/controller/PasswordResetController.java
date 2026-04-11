package com.spring.jwt.controller;

import com.spring.jwt.dto.ResetPassword;
import com.spring.jwt.service.PasswordResetService;
import com.spring.jwt.utils.ResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Password Reset Controller
 * 
 * Handles all password reset operations with:
 * - Rate limiting (configured in RateLimitingAspect)
 * - Token-based password reset
 * - Email verification
 * - Security best practices
 * 
 * @author System
 * @version 2.0
 */
@Tag(
    name = "Password Reset API",
    description = "Secure password reset endpoints with rate limiting and token validation"
)
@RestController
@RequestMapping("/api/auth/v1/password")
@RequiredArgsConstructor
@Validated
@Slf4j
@CrossOrigin(origins = "${app.cors.allowed-origins}", maxAge = 3600)
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    /**
     * Request password reset
     * 
     * Rate Limited: 3 requests per 15 minutes per email
     * Token Expiry: 30 minutes
     */
    @Operation(
        summary = "Request password reset",
        description = "Sends a password reset email with a secure token. Rate limited to prevent abuse."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Password reset email sent successfully",
            content = @Content(schema = @Schema(implementation = ResponseDto.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "User not found with provided email"
        ),
        @ApiResponse(
            responseCode = "429",
            description = "Too many requests - rate limit exceeded"
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Failed to send email"
        )
    })
    @PostMapping("/forgot")
    public ResponseEntity<ResponseDto> forgotPassword(
            @RequestParam @Email(message = "Invalid email format") String email) {
        
        log.info("Password reset requested for email: {}", maskEmail(email));
        ResponseDto response = passwordResetService.initiatePasswordReset(email);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Validate password reset token
     * 
     * Checks if token is valid and not expired
     */
    @Operation(
        summary = "Validate reset token",
        description = "Validates if the password reset token is valid and not expired"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Token validation result"
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid token format"
        )
    })
    @GetMapping("/validate-token")
    public ResponseEntity<ResponseDto> validateToken(
            @RequestParam String token) {
        
        boolean isValid = passwordResetService.validateResetToken(token);
        
        ResponseDto response = new ResponseDto(
            isValid ? "Valid" : "Invalid",
            isValid ? "Token is valid" : "Token is invalid or expired"
        );
        
        return ResponseEntity.ok(response);
    }

    /**
     * Reset password with token
     * 
     * Rate Limited: 5 requests per 15 minutes per IP
     * 
     * Validates:
     * - Token validity
     * - Password match
     * - Password not same as old password
     * - Password strength (handled by validation)
     */
    @Operation(
        summary = "Reset password",
        description = "Resets user password using the token from email. Validates password strength and ensures new password is different from old one."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Password reset successful"
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request - passwords don't match, token invalid, or same as old password"
        ),
        @ApiResponse(
            responseCode = "429",
            description = "Too many requests - rate limit exceeded"
        )
    })
    @PostMapping("/reset")
    public ResponseEntity<ResponseDto> resetPassword(
            @Valid @RequestBody ResetPassword request) {
        
        log.info("Password reset attempt with token");
        ResponseDto response = passwordResetService.resetPassword(request);
        
        HttpStatus status = "Successful".equals(response.getStatus()) 
            ? HttpStatus.OK 
            : HttpStatus.BAD_REQUEST;
        
        return ResponseEntity.status(status).body(response);
    }

    /**
     * Masks email for logging (security best practice)
     */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int atIndex = email.indexOf('@');
        return email.charAt(0) + "***" + email.substring(atIndex);
    }
}
