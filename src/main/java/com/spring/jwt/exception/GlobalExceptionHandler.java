package com.spring.jwt.exception;

import com.spring.jwt.dto.ErrorResponseDTO;
import com.spring.jwt.utils.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ErrorResponseDTO> handleBaseException(BaseException ex, HttpServletRequest request) {
        log.error("Base exception: {} - {}", ex.getCode(), ex.getMessage());

        HttpStatus status = determineHttpStatus(ex);
        
        return buildResponse(status, ex.getCode() != null ? ex.getCode() : "APPLICATION_ERROR", 
                           ex.getMessage() != null ? ex.getMessage() : "An application error occurred", 
                           request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleResourceNotFoundException(ResourceNotFoundException ex,
            HttpServletRequest request) {
        log.error("Resource not found: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(ResourceAlreadyExistsException.class)
    public ResponseEntity<ErrorResponseDTO> handleResourceAlreadyExistsException(ResourceAlreadyExistsException ex,
            HttpServletRequest request) {
        log.error("Resource already exists: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, "RESOURCE_ALREADY_EXISTS", ex.getMessage(), request);
    }

    @ExceptionHandler(AttendanceException.class)
    public ResponseEntity<ApiResponse<Void>> handleAttendanceException(AttendanceException ex) {
        log.error("Attendance exception: {}", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(HttpStatus.BAD_REQUEST, ex.getMessage(), null));
    }
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponseDTO> handleOptimisticLockingFailureException(
            OptimisticLockingFailureException ex, HttpServletRequest request) {
        log.error("Optimistic locking failure: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, "CONCURRENT_UPDATE_FAILURE",
                "Data was updated by another user. Please refresh and try again.", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDTO> handleAccessDeniedException(AccessDeniedException ex,
            HttpServletRequest request) {
        log.warn("Access denied (caught by GlobalExceptionHandler): {} - Path: {} - Method: {} - User attempted to access admin endpoint without proper role", 
                ex.getMessage(), request.getRequestURI(), request.getMethod());
        
        // Provide context-aware error message
        String message = "You do not have permission to access this resource.";
        if (request.getRequestURI().contains("/user/")) {
            message = "Admin role required to access user-specific resources.";
        } else if (request.getRequestURI().contains("/admin")) {
            message = "Admin role required to access administrative resources.";
        } else if (request.getMethod().equals("GET") && (request.getRequestURI().endsWith("/profiles") || 
                   request.getRequestURI().endsWith("/horoscope") || request.getRequestURI().endsWith("/family-background"))) {
            message = "Admin role required to browse all resources.";
        }
        
        return buildResponse(HttpStatus.FORBIDDEN, "ACCESS_DENIED", message, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidationExceptions(MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        log.error("Validation error: {}", errors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseDTO.builder()
                        .status(HttpStatus.BAD_REQUEST.value())
                        .errorCode("VALIDATION_ERROR")
                        .message("Validation failed")
                        .details(errors.toString())
                        .path(request.getRequestURI())
                        .method(request.getMethod())
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDTO> handleIllegalArgumentException(IllegalArgumentException ex,
            HttpServletRequest request) {
        log.error("Validation error: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage(), request);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponseDTO> handleRuntimeException(RuntimeException ex,
            HttpServletRequest request) {
        log.error("Runtime error: {}", ex.getMessage(), ex);
        
        // Check if it's a specific business logic error
        String message = ex.getMessage();
        if (message != null) {
            if (message.contains("Profile not found") || message.contains("User not found")) {
                return buildResponse(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", 
                    "The target user's profile is not complete. Please try again later.", request);
            }
            if (message.contains("already exists") || message.contains("duplicate")) {
                return buildResponse(HttpStatus.CONFLICT, "INTEREST_ALREADY_EXISTS", 
                    "You have already expressed interest in this user.", request);
            }
            if (message.contains("daily limit")) {
                return buildResponse(HttpStatus.TOO_MANY_REQUESTS, "DAILY_LIMIT_EXCEEDED", 
                    "You have reached your daily limit for expressing interest. Try again tomorrow.", request);
            }
            if (message.contains("subscription") || message.contains("premium")) {
                return buildResponse(HttpStatus.PAYMENT_REQUIRED, "SUBSCRIPTION_REQUIRED", 
                    "An active subscription is required to express interest.", request);
            }
        }
        
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", 
            "An unexpected error occurred. Please try again later.", request);
    }

    private ResponseEntity<ErrorResponseDTO> buildResponse(HttpStatus status, String errorCode, String message,
            HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(ErrorResponseDTO.builder()
                        .status(status.value())
                        .errorCode(errorCode)
                        .message(message)
                        .path(request.getRequestURI())
                        .method(request.getMethod())
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    /**
     * Determine appropriate HTTP status based on BaseException details
     */
    private HttpStatus determineHttpStatus(BaseException ex) {
        String message = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        String code = ex.getCode() != null ? ex.getCode().toUpperCase() : "";

        if (code.contains("PAYMENT_IDEMPOTENCY_CONFLICT")) {
            return HttpStatus.CONFLICT;
        }

        // Check for specific error patterns
        if (message.contains("already registered") || message.contains("already exists") || 
            code.contains("ALREADY_EXISTS") || code.contains("DUPLICATE")) {
            return HttpStatus.CONFLICT; // 409
        }
        
        if (message.contains("not found") || code.contains("NOT_FOUND")) {
            return HttpStatus.NOT_FOUND; // 404
        }
        
        if (message.contains("invalid") || message.contains("validation") || 
            code.contains("INVALID") || code.contains("VALIDATION")) {
            return HttpStatus.BAD_REQUEST;
        }
        
        if (message.contains("unauthorized") || message.contains("access denied") || 
            code.contains("UNAUTHORIZED") || code.contains("ACCESS_DENIED")) {
            return HttpStatus.FORBIDDEN;
        }

        return HttpStatus.BAD_REQUEST;
    }

    @ExceptionHandler(DocumentAlreadyExistsException.class)
    public ResponseEntity<ErrorResponseDTO> handleDocumentAlreadyExistsException(
            DocumentAlreadyExistsException ex,
            HttpServletRequest request) {

        log.error("Duplicate document: {}", ex.getMessage());

        return buildResponse(
                HttpStatus.CONFLICT,
                "RESOURCE_ALREADY_EXISTS",
                ex.getMessage(),
                request
        );
    }

}
