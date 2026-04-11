package com.spring.jwt.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standardized error response with detailed information")
public class ErrorResponseDTO {

    @Schema(description = "HTTP status code", example = "400")
    private Integer status;

    @Schema(description = "Machine-readable error code for programmatic handling", example = "AUTHENTICATION_REQUIRED")
    private String errorCode;

    @Schema(description = "Human-readable error message", example = "Authentication token is required")
    private String message;

    @Schema(description = "Detailed error description with context", example = "Please provide a valid JWT token in the Authorization header")
    private String details;

    @Schema(description = "API endpoint that caused the error", example = "/api/v1/documents/upload")
    private String path;

    @Schema(description = "HTTP method used", example = "POST")
    private String method;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "Timestamp when error occurred")
    private LocalDateTime timestamp;

    @Schema(description = "Suggested actions to resolve the error")
    private List<String> suggestedActions;

    @Schema(description = "Field-specific validation errors")
    private Map<String, String> fieldErrors;

    @Schema(description = "Additional context or debugging information")
    private Map<String, Object> additionalInfo;

    @Schema(description = "Reference documentation URL", example = "https://api-docs.example.com/errors/AUTHENTICATION_REQUIRED")
    private String documentationUrl;

    /**
     * Create error response for authentication failures
     */
    public static ErrorResponseDTO authenticationRequired(String path, String method) {
        return ErrorResponseDTO.builder()
                .status(401)
                .errorCode("AUTHENTICATION_REQUIRED")
                .message("Authentication token is required")
                .details("Please provide a valid JWT token in the Authorization header using Bearer scheme")
                .path(path)
                .method(method)
                .timestamp(LocalDateTime.now())
                .suggestedActions(List.of(
                        "Include 'Authorization: Bearer <your-jwt-token>' header in your request",
                        "Ensure your JWT token is valid and not expired",
                        "Login again to get a fresh token if needed"
                ))
                .documentationUrl("/docs/authentication")
                .build();
    }

    /**
     * Create error response for authorization failures
     */
    public static ErrorResponseDTO accessDenied(String path, String method, String resource) {
        return ErrorResponseDTO.builder()
                .status(403)
                .errorCode("ACCESS_DENIED")
                .message("Access denied to the requested resource")
                .details(String.format("You don't have permission to access %s", resource))
                .path(path)
                .method(method)
                .timestamp(LocalDateTime.now())
                .suggestedActions(List.of(
                        "Verify you have the required permissions",
                        "Contact administrator if you believe this is an error",
                        "Ensure you're accessing your own resources"
                ))
                .build();
    }

    /**
     * Create error response for validation failures
     */
    public static ErrorResponseDTO validationFailed(String path, String method, Map<String, String> fieldErrors) {
        return ErrorResponseDTO.builder()
                .status(400)
                .errorCode("VALIDATION_FAILED")
                .message("Request validation failed")
                .details("One or more fields contain invalid values")
                .path(path)
                .method(method)
                .timestamp(LocalDateTime.now())
                .fieldErrors(fieldErrors)
                .suggestedActions(List.of(
                        "Check the fieldErrors for specific validation issues",
                        "Ensure all required fields are provided",
                        "Verify field formats match the expected patterns"
                ))
                .build();
    }

    /**
     * Create error response for file upload issues
     */
    public static ErrorResponseDTO fileUploadError(String path, String method, String reason, long maxSizeMB) {
        return ErrorResponseDTO.builder()
                .status(400)
                .errorCode("FILE_UPLOAD_ERROR")
                .message("File upload failed")
                .details(reason)
                .path(path)
                .method(method)
                .timestamp(LocalDateTime.now())
                .suggestedActions(List.of(
                        String.format("Ensure file size is less than %dMB", maxSizeMB),
                        "Use supported file formats: PDF, JPEG, PNG, WEBP",
                        "Check that the file is not corrupted",
                        "Verify the file parameter name is 'file'"
                ))
                .additionalInfo(Map.of("maxFileSizeMB", maxSizeMB))
                .build();
    }

    /**
     * Create error response for resource not found
     */
    public static ErrorResponseDTO resourceNotFound(String path, String method, String resourceType, String identifier) {
        return ErrorResponseDTO.builder()
                .status(404)
                .errorCode("RESOURCE_NOT_FOUND")
                .message(String.format("%s not found", resourceType))
                .details(String.format("No %s found with identifier: %s", resourceType.toLowerCase(), identifier))
                .path(path)
                .method(method)
                .timestamp(LocalDateTime.now())
                .suggestedActions(List.of(
                        "Verify the resource identifier is correct",
                        "Check if the resource exists and you have access to it",
                        "Ensure you're using the correct API endpoint"
                ))
                .build();
    }

    /**
     * Create error response for server errors
     */
    public static ErrorResponseDTO internalServerError(String path, String method, String details) {
        return ErrorResponseDTO.builder()
                .status(500)
                .errorCode("INTERNAL_SERVER_ERROR")
                .message("An internal server error occurred")
                .details(details != null ? details : "The server encountered an unexpected condition")
                .path(path)
                .method(method)
                .timestamp(LocalDateTime.now())
                .suggestedActions(List.of(
                        "Try the request again after a few moments",
                        "Contact support if the problem persists",
                        "Check system status page for known issues"
                ))
                .build();
    }
}