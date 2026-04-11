package com.spring.jwt.Document.Service;

import com.spring.jwt.Enums.DocumentType;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service interface for file validation operations
 * Follows Single Responsibility Principle
 */
public interface FileValidationService {

    /**
     * Validate uploaded file for basic requirements
     * 
     * @param file the uploaded file
     * @throws com.spring.jwt.exception.InvalidDocumentException if validation fails
     */
    void validateFile(MultipartFile file);

    /**
     * Validate file for specific document type requirements
     * 
     * @param file the uploaded file
     * @param documentType the target document type
     * @throws com.spring.jwt.exception.InvalidDocumentException if validation fails
     */
    void validateFileForDocumentType(MultipartFile file, DocumentType documentType);

    /**
     * Check if file type is supported
     * 
     * @param contentType the MIME type of the file
     * @return true if supported, false otherwise
     */
    boolean isSupportedFileType(String contentType);

    /**
     * Check if file size is within limits
     * 
     * @param fileSize the size of the file in bytes
     * @return true if within limits, false otherwise
     */
    boolean isFileSizeValid(long fileSize);

    /**
     * Get maximum allowed file size in bytes
     * 
     * @return maximum file size in bytes
     */
    long getMaxFileSizeBytes();
}