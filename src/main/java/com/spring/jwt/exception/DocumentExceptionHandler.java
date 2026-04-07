package com.spring.jwt.exception;

import com.spring.jwt.dto.ErrorResponseDTO;
import com.spring.jwt.config.DocumentProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

/**
 * Specialized exception handler for document management APIs
 * 
 * This handler focuses only on document-specific exceptions that need
 * specialized handling beyond what the global exception handler provides.
 * It works in conjunction with GlobalException handler, not as a replacement.
 *
 * @author Document Management System
 * @since 2.0
 */
@RestControllerAdvice(basePackages = "com.spring.jwt.Document")
@Slf4j
@RequiredArgsConstructor
public class DocumentExceptionHandler
{

    private final DocumentProperties documentProperties;

    /**
     * Handle file upload size exceeded errors - Document specific handling with file size info
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponseDTO> handleMaxUploadSizeExceeded
    (
            MaxUploadSizeExceededException ex, HttpServletRequest request
    )
    {

        log.warn("File upload size exceeded for document request: {} {}", request.getMethod(), request.getRequestURI());

        long maxSizeMB = documentProperties.getMaxFileSizeMB();

        ErrorResponseDTO error = ErrorResponseDTO.fileUploadError(
                request.getRequestURI(),
                request.getMethod(),
                String.format("File size exceeds the maximum allowed limit of %dMB", maxSizeMB),
                maxSizeMB
        );

        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(error);
    }

    /**
     * Handle multipart/file upload errors - Document specific handling
     */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponseDTO> handleMultipartException
    (
            MultipartException ex, HttpServletRequest request
    )
    {

        log.warn("Multipart upload error for document request: {} {} - {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());

        String reason;
        if (ex.getCause() instanceof MaxUploadSizeExceededException)
        {
            reason = String.format("File size exceeds the maximum allowed limit of %dMB",
                    documentProperties.getMaxFileSizeMB());
        } else
        {
            reason = "Invalid file upload. Please ensure you're uploading a valid file.";
        }

        ErrorResponseDTO error = ErrorResponseDTO.fileUploadError
                (
                request.getRequestURI(),
                request.getMethod(),
                reason,
                documentProperties.getMaxFileSizeMB()
                );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }



    /**
     * Handle document-specific business validation exceptions
     */
    @ExceptionHandler(InvalidDocumentException.class)
    public ResponseEntity<ErrorResponseDTO> handleInvalidDocumentException
    (
            InvalidDocumentException ex, HttpServletRequest request
    )
    {

        log.warn("Invalid document error for request: {} {} - {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());

        ErrorResponseDTO error = ErrorResponseDTO.fileUploadError(
                request.getRequestURI(),
                request.getMethod(),
                ex.getMessage(),
                documentProperties.getMaxFileSizeMB()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }


}