package com.spring.jwt.Document.Service;

import com.spring.jwt.Enums.DocumentType;

/**
 * Service interface for document security and authorization
 * Handles user permissions and document access control
 */
public interface DocumentSecurityService
{

    /**
     * Check if user can upload document of specified type
     * 
     * @param userId the user ID
     * @param documentType the document type
     * @return true if allowed, false otherwise
     */
    boolean canUploadDocument(Long userId, DocumentType documentType);

    /**
     * Check if user can access document
     * 
     * @param userId the user ID
     * @param documentId the document ID
     * @return true if allowed, false otherwise
     */
    boolean canAccessDocument(Long userId, Integer documentId);

    /**
     * Check if user can modify document
     * 
     * @param userId the user ID
     * @param documentId the document ID
     * @return true if allowed, false otherwise
     */
    boolean canModifyDocument(Long userId, Integer documentId);

    /**
     * Check if user can delete document
     * 
     * @param userId the user ID
     * @param documentId the document ID
     * @return true if allowed, false otherwise
     */
    boolean canDeleteDocument(Long userId, Integer documentId);

    /**
     * Validate user ownership of document
     * 
     * @param userId the user ID
     * @param documentId the document ID
     * @throws com.spring.jwt.exception.UnauthorizedAccessException if not authorized
     */
    void validateDocumentOwnership(Long userId, Integer documentId);

    /**
     * Check if document type allows multiple uploads per user
     * 
     * @param documentType the document type
     * @return true if multiple uploads allowed, false otherwise
     */
    boolean allowsMultipleUploads(DocumentType documentType);
}