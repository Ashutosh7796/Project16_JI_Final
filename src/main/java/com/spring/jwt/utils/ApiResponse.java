package com.spring.jwt.utils;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

/**
 * Generic API response wrapper for standardized API responses
 * @param <T> Type of the data payload
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(
        name = "ApiResponse",
        description = "Standardized API response format"
)
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private HttpStatus status;
    private int statusCode;
    private String errorCode;
    private String errorDetails;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @Builder.Default
    @Schema(description = "Timestamp of the response")
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * Constructor for complete ApiResponse
     */
    public ApiResponse(boolean success, String message, T data, HttpStatus status, 
                      String errorCode, String errorDetails) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.status = status;
        this.statusCode = status != null ? status.value() : 0;
        this.errorCode = errorCode;
        this.errorDetails = errorDetails;
        this.timestamp = LocalDateTime.now();
    }
    
    /**
     * Constructor for ApiResponse with status code as integer 
     */
    public ApiResponse(int statusCode, String message, T data) {
        this.success = statusCode >= 200 && statusCode < 300;
        this.message = message;
        this.data = data;
        this.statusCode = statusCode;
        this.timestamp = LocalDateTime.now();
    }

    /**
     * Create a success response with data
     * @param message Success message
     * @param data Response data
     * @param <T> Type of data
     * @return ApiResponse object
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .status(HttpStatus.OK)
                .statusCode(HttpStatus.OK.value())
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Create a success response without data
     * @param message Success message
     * @param <T> Type of data
     * @return ApiResponse object
     */
    public static <T> ApiResponse<T> success(String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .status(HttpStatus.OK)
                .statusCode(HttpStatus.OK.value())
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Create an error response
     * @param status HTTP status
     * @param message Error message
     * @param errorDetails Detailed error information
     * @param <T> Type of data
     * @return ApiResponse object
     */
    public static <T> ApiResponse<T> error(HttpStatus status, String message, String errorDetails) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .status(status)
                .statusCode(status.value())
                .errorDetails(errorDetails)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Create an error response with error code
     * @param status HTTP status
     * @param message Error message
     * @param errorCode Error code
     * @param errorDetails Detailed error information
     * @param <T> Type of data
     * @return ApiResponse object
     */
    public static <T> ApiResponse<T> error(HttpStatus status, String message, String errorCode, String errorDetails) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .status(status)
                .statusCode(status.value())
                .errorCode(errorCode)
                .errorDetails(errorDetails)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Create a simple error response
     * @param message Error message
     * @param errorDetails Detailed error information
     * @param <T> Type of data
     * @return ApiResponse object
     */
    public static <T> ApiResponse<T> error(String message, String errorDetails) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .status(HttpStatus.BAD_REQUEST)
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .errorDetails(errorDetails)
                .timestamp(LocalDateTime.now())
                .build();
    }
} 