package com.spring.jwt.Document.Service.Impl;

import com.spring.jwt.Document.DocumentRepository;
import com.spring.jwt.Document.Service.DocumentSecurityService;
import com.spring.jwt.Enums.DocumentType;
import com.spring.jwt.entity.Document;
import com.spring.jwt.exception.DocumentNotFoundException;
import com.spring.jwt.exception.UnauthorizedAccessException;
import com.spring.jwt.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Implementation of document security service
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentSecurityServiceImpl implements DocumentSecurityService {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;

    private static final Set<DocumentType> MULTIPLE_UPLOAD_ALLOWED = Set.of(
            DocumentType.EDUCATION_CERTIFICATE,
            DocumentType.OTHER);

    private static final Set<DocumentType> RESTRICTED_DOCUMENT_TYPES = Set.of(
            DocumentType.AADHAAR_CARD,
            DocumentType.PAN_CARD,
            DocumentType.PASSPORT);

    @Override
    public boolean canUploadDocument(Long userId, DocumentType documentType) {
        log.debug("Checking upload permission for user {} and document type {}", userId, documentType);

        if (!userRepository.existsById(userId)) {
            log.warn("Upload denied: User {} not found", userId);
            return false;
        }

        return true;
    }

    @Override
    public boolean canAccessDocument(Long userId, Integer documentId) {
        log.debug("Checking access permission for user {} and document {}", userId, documentId);

        // Admins can access any document
        if (isCurrentUserAdmin()) {
            return true;
        }

        try {
            Document document = findDocumentById(documentId);
            boolean canAccess = document.getUser().getUserId().equals(userId);

            if (!canAccess) {
                log.warn("Access denied: Document {} does not belong to user {}", documentId, userId);
            }

            return canAccess;
        } catch (DocumentNotFoundException e) {
            log.warn("Access denied: Document {} not found", documentId);
            return false;
        }
    }

    @Override
    public boolean canModifyDocument(Long userId, Integer documentId) {
        // Admins can modify any document
        if (isCurrentUserAdmin()) {
            return true;
        }
        return canAccessDocument(userId, documentId);
    }

    @Override
    public boolean canDeleteDocument(Long userId, Integer documentId) {
        // Admins can delete any document
        if (isCurrentUserAdmin()) {
            return true;
        }

        if (!canAccessDocument(userId, documentId)) {
            return false;
        }

        try {
            Document document = findDocumentById(documentId);
            DocumentType documentType = document.getDocumentType();

            if (RESTRICTED_DOCUMENT_TYPES.contains(documentType)) {
                log.warn("Deletion restricted for document type: {}", documentType);
            }

            return true;
        } catch (DocumentNotFoundException e) {
            return false;
        }
    }

    @Override
    public void validateDocumentOwnership(Long userId, Integer documentId) {
        log.debug("Validating document ownership for user {} and document {}", userId, documentId);

        // Admins bypass ownership check
        if (isCurrentUserAdmin()) {
            log.debug("Admin access granted for document {}", documentId);
            return;
        }

        if (!canAccessDocument(userId, documentId)) {
            throw new UnauthorizedAccessException(
                    String.format("User %d is not authorized to access document %d", userId, documentId));
        }
    }

    /**
     * Checks if the currently authenticated user has the ADMIN role.
     */
    private boolean isCurrentUserAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @Override
    public boolean allowsMultipleUploads(DocumentType documentType) {
        return MULTIPLE_UPLOAD_ALLOWED.contains(documentType);
    }


    private Document findDocumentById(Integer documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(
                        "Document not found with ID: " + documentId));
    }
}