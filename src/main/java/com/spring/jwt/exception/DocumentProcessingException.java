package com.spring.jwt.exception;

/**
 * Exception thrown when document processing operations fail
 *
 * This exception is used to wrap various processing errors including:
 * - File compression failures
 * - Image processing errors
 * - PDF processing errors
 * - File format conversion issues
 *
 * @author Ashutosh Shedge
 * @since 1.0
 */
public class DocumentProcessingException extends RuntimeException {

    /**
     * Constructs a new DocumentProcessingException with the specified detail message
     *
     * @param message the detail message explaining the processing failure
     */
    public DocumentProcessingException(String message) {
        super(message);
    }

    /**
     * Constructs a new DocumentProcessingException with the specified detail message and cause
     *
     * @param message the detail message explaining the processing failure
     * @param cause the cause of the processing failure (which is saved for later retrieval)
     */
    public DocumentProcessingException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new DocumentProcessingException with the specified cause
     *
     * @param cause the cause of the processing failure (which is saved for later retrieval)
     */
    public DocumentProcessingException(Throwable cause) {
        super(cause);
    }
}
