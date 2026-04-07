package com.spring.jwt.Document.Service;

import com.spring.jwt.Document.domain.FileProcessingResult;
import com.spring.jwt.Enums.DocumentType;
import com.spring.jwt.entity.Document;
import com.spring.jwt.entity.User;
import com.spring.jwt.exception.UserNotFoundExceptions;
import com.spring.jwt.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Helper utility class for Document Service operations
 * Contains reusable helper methods extracted from DocumentServiceImpl
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DocumentServiceHelper
{

    private final UserRepository userRepository;

    /**
     * Validate upload input parameters
     */
    public void validateUploadInputs(Long userId, MultipartFile file, DocumentType documentType)
    {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (file == null) {
            throw new IllegalArgumentException("File cannot be null");
        }
        if (documentType == null) {
            throw new IllegalArgumentException("Document type cannot be null");
        }
    }

    /**
     * Validate user ID and check if user exists
     */
    public void validateUserId(Long userId)
    {
        if (userId == null)
        {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundExceptions("User not found with ID: " + userId);
        }
    }

    /**
     * Get user by ID with proper error handling
     */
    public User getUserById(Long userId)
    {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundExceptions("User not found with ID: " + userId));
    }

    /**
     * Create document entity from upload data
     */
    public Document createDocumentEntity(User user, MultipartFile file, DocumentType documentType,
                                       String description, FileProcessingResult processingResult)
    {
        return Document.builder()
                .documentType(documentType)
                .fileName(file.getOriginalFilename())
                .description(description != null ? description.trim() : null)
                .fileSize(processingResult.getProcessedSize())
                .contentType(file.getContentType())
                .fileData(processingResult.getProcessedData())
                .user(user)
                .build();
    }

    /**
     * Update document file data with new file information
     */
    public void updateDocumentFileData(Document document, MultipartFile file, FileProcessingResult processingResult)
    {
        document.setFileName(file.getOriginalFilename());
        document.setFileSize(processingResult.getProcessedSize());
        document.setContentType(file.getContentType());
        document.setFileData(processingResult.getProcessedData());
    }

    /**
     * Validate pagination parameters
     */
    public void validatePaginationParameters(int page, int size, int maxSize)
    {
        if (page < 0)
        {
            throw new IllegalArgumentException("Page number cannot be negative");
        }
        if (size <= 0 || size > maxSize)
        {
            throw new IllegalArgumentException(
                    String.format("Page size must be between 1 and %d", maxSize));
        }
    }
}