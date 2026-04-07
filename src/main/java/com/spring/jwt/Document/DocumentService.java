package com.spring.jwt.Document;

import com.spring.jwt.Document.domain.DocumentMetadata;
import com.spring.jwt.Enums.DocumentType;
import com.spring.jwt.dto.DocumentDetailResponseDTO;
import com.spring.jwt.dto.DocumentResponseDTO;
import com.spring.jwt.dto.PaginatedDocumentResponseDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

/**
 * Enhanced Document Service interface following enterprise patterns
 * Provides comprehensive document management operations
 */
public interface DocumentService {

        /**
         * Upload a document for a user with comprehensive validation and processing
         * 
         * @param userId       the user ID
         * @param file         the uploaded file
         * @param documentType the type of document
         * @param description  optional description
         * @return document response DTO
         */
        DocumentResponseDTO uploadDocument(Long userId, MultipartFile file,
                        DocumentType documentType, String description);

        /**
         * Get document by ID with file data
         * 
         * @param userId     the user ID (for authorization)
         * @param documentId the document ID
         * @return document detail response DTO
         */
        DocumentDetailResponseDTO getDocumentById(Long userId, Integer documentId);

        /**
         * Get document by document type
         * 
         * @param userId       the user ID
         * @param documentType the document type
         * @return document detail response DTO
         */
        DocumentDetailResponseDTO getDocumentByType(Long userId, DocumentType documentType);

        /**
         * Get all documents for a user (without file data for performance)
         * 
         * @param userId the user ID
         * @return list of document response DTOs
         */
        List<DocumentResponseDTO> getAllDocumentsByUserId(Long userId);

        /**
         * Get documents by multiple types for a user
         * 
         * @param userId        the user ID
         * @param documentTypes list of document types
         * @return list of document response DTOs
         */
        List<DocumentResponseDTO> getDocumentsByTypes(Long userId, List<DocumentType> documentTypes);

        /**
         * Update document (replace existing document or update metadata)
         * 
         * @param userId      the user ID
         * @param documentId  the document ID
         * @param file        optional new file (null to keep existing)
         * @param description optional new description (null to keep existing)
         * @return updated document response DTO
         */
        DocumentResponseDTO updateDocument(Long userId, Integer documentId,
                        MultipartFile file, String description);

        /**
         * Delete document by ID
         * 
         * @param userId     the user ID (for authorization)
         * @param documentId the document ID
         */
        void deleteDocument(Long userId, Integer documentId);

        /**
         * Delete document by type
         * 
         * @param userId       the user ID
         * @param documentType the document type
         */
        void deleteDocumentByType(Long userId, DocumentType documentType);

        /**
         * Check if document exists for user and type
         * 
         * @param userId       the user ID
         * @param documentType the document type
         * @return true if exists, false otherwise
         */
        boolean documentExists(Long userId, DocumentType documentType);

        /**
         * Get document count for user
         * 
         * @param userId the user ID
         * @return total number of documents
         */
        long getDocumentCount(Long userId);

        /**
         * Get document metadata without file data
         * 
         * @param userId     the user ID
         * @param documentId the document ID
         * @return document metadata
         */
        Optional<DocumentMetadata> getDocumentMetadata(Long userId, Integer documentId);

        /**
         * Get documents by user with pagination support
         * 
         * @param userId the user ID
         * @param page   page number (0-based)
         * @param size   page size
         * @return paginated document response DTO
         */
        PaginatedDocumentResponseDTO getDocumentsPaginated(Long userId, int page, int size);

        /**
         * Replace existing document of the same type
         * 
         * @param userId       the user ID
         * @param file         the new file
         * @param documentType the document type
         * @param description  optional description
         * @return document response DTO
         */
        DocumentResponseDTO replaceDocument(Long userId, MultipartFile file,
                        DocumentType documentType, String description);

        /**
         * Download document file data
         * Security: Validates user ownership before allowing download
         * 
         * @param userId     the user ID (for authorization)
         * @param documentId the document ID
         * @return document file data as byte array
         */
        byte[] downloadDocument(Long userId, Integer documentId);

}
