package com.spring.jwt.Document;

import com.spring.jwt.Document.Service.DocumentSecurityService;
import com.spring.jwt.Document.Service.DocumentServiceHelper;
import com.spring.jwt.Document.Service.FileProcessingService;
import com.spring.jwt.Document.Service.FileValidationService;
import com.spring.jwt.Document.domain.DocumentMetadata;
import com.spring.jwt.Document.domain.FileProcessingResult;
import com.spring.jwt.Enums.DocumentType;
import com.spring.jwt.config.DocumentProperties;
import com.spring.jwt.dto.DocumentDetailResponseDTO;
import com.spring.jwt.dto.DocumentResponseDTO;
import com.spring.jwt.dto.PaginatedDocumentResponseDTO;
import com.spring.jwt.entity.Document;
import com.spring.jwt.entity.User;
import com.spring.jwt.exception.DocumentNotFoundException;
import com.spring.jwt.exception.DocumentProcessingException;
import com.spring.jwt.exception.UnauthorizedAccessException;
import com.spring.jwt.mapper.DocumentResponseMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentResponseMapper documentResponseMapper;
    private final FileValidationService fileValidationService;
    private final FileProcessingService fileProcessingService;
    private final DocumentSecurityService documentSecurityService;
    private final DocumentProperties documentProperties;
    private final DocumentServiceHelper documentServiceHelper;

    @Override
    @Transactional
    public DocumentResponseDTO uploadDocument(Long userId, MultipartFile file,
            DocumentType documentType, String description) {
        documentServiceHelper.validateUploadInputs(userId, file, documentType);

        User user = documentServiceHelper.getUserById(userId);

        fileValidationService.validateFileForDocumentType(file, documentType);

        if (!documentSecurityService.allowsMultipleUploads(documentType))
        {
            Optional<Document> existing = documentRepository.findByUserIdAndDocumentType(userId, documentType);
            if (existing.isPresent())
            {
                // Admin can replace any document; regular users get a clear error
                if (isCurrentUserAdmin()) {
                    log.info("Admin replacing existing document of type {} for user {}", documentType, userId);
                    return updateDocument(userId, existing.get().getDocumentId(), file, description);
                }
                throw new com.spring.jwt.exception.DocumentAlreadyExistsException(
                        String.format("Document type %s already exists for user %d. Use the replace endpoint to update it.", documentType, userId));
            }
        }

        if (!documentSecurityService.canUploadDocument(userId, documentType))
        {
            throw new UnauthorizedAccessException(
                    String.format("User %d is not authorized to upload document type %s",
                            userId, documentType));
        }

        try {
            long startTime = System.currentTimeMillis();

            FileProcessingResult fileProcessingResult = fileProcessingService
                    .processFile(file, documentType)
                    .join();

            Document document = documentServiceHelper.createDocumentEntity(user, file, documentType, description,
                    fileProcessingResult);
            Document savedDocument = documentRepository.save(document);

            long totalTime = System.currentTimeMillis() - startTime;
            log.info("Document '{}' uploaded successfully for user {} with ID {} in {}ms ({})",
                    file.getOriginalFilename(), userId, savedDocument.getDocumentId(), totalTime,
                    fileProcessingResult.getProcessingSummary());
            return documentResponseMapper.toResponseDTO(savedDocument)
                    .orElseThrow(() -> new DocumentProcessingException("Failed to map saved document to DTO"));
        } catch (Exception e)
        {
            log.error("Document upload failed for user {}: {}", userId, e.getMessage(), e);
            throw new DocumentProcessingException("Failed to upload document: " + e.getMessage(), e);
        }
    }

    @Override
    public DocumentDetailResponseDTO getDocumentById(Long userId, Integer documentId)
    {
        log.debug("Fetching document {} for user {}", documentId, userId);

        documentSecurityService.validateDocumentOwnership(userId, documentId);
        Document document = getDocumentEntityById(documentId);
        return documentResponseMapper.toDetailResponseDTO(document)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found with ID: " + documentId));
    }

    @Override
    public DocumentDetailResponseDTO getDocumentByType(Long userId, DocumentType documentType)
    {
        log.debug("Fetching document of type {} for user {}", documentType, userId);
        documentServiceHelper.validateUserId(userId);

        Document document = documentRepository.findByUserIdAndDocumentType(userId, documentType)
                .orElseThrow(() -> new DocumentNotFoundException(
                        String.format("Document of type %s not found for user %d", documentType, userId)));

        return documentResponseMapper.toDetailResponseDTO(document)
                .orElseThrow(() -> new DocumentNotFoundException(
                        String.format("Document of type %s not found for user %d", documentType, userId)));
    }

    @Override
    public List<DocumentResponseDTO> getAllDocumentsByUserId(Long userId)
    {
        log.debug("Fetching all documents for user {}", userId);
        documentServiceHelper.validateUserId(userId);

        List<Document> documents = documentRepository.findByUserIdOrderByUploadedAtDesc(userId);
        return documentResponseMapper.toResponseDTOList(documents);
    }

    @Override
    public List<DocumentResponseDTO> getDocumentsByTypes(Long userId, List<DocumentType> documentTypes)
    {
        log.debug("Fetching documents of types {} for user {}", documentTypes, userId);
        documentServiceHelper.validateUserId(userId);

        if (documentTypes == null || documentTypes.isEmpty())
        {
            return getAllDocumentsByUserId(userId);
        }

        List<Document> documents = documentRepository.findByUserIdAndDocumentTypeIn(userId, documentTypes);
        return documentResponseMapper.toResponseDTOList(documents);
    }

    @Override
    public DocumentResponseDTO updateDocument(Long userId, Integer documentId, MultipartFile file,
            String description)
    {
        log.info("Updating document {} for user {}", documentId, userId);

        documentSecurityService.validateDocumentOwnership(userId, documentId);

        if (!documentSecurityService.canModifyDocument(userId, documentId))
        {
            throw new UnauthorizedAccessException(
                    String.format("User %d is not authorized to modify document %d", userId, documentId));
        }
        Document existingDocument = getDocumentEntityById(documentId);

        try {
            boolean fileUpdated = false;

            if (file != null && !file.isEmpty())
            {
                fileValidationService.validateFileForDocumentType(file, existingDocument.getDocumentType());

                FileProcessingResult processingResult = fileProcessingService
                        .processFile(file, existingDocument.getDocumentType())
                        .join();

                documentServiceHelper.updateDocumentFileData(existingDocument, file, processingResult);
                fileUpdated = true;
            }
            if (description != null && !description.trim().isEmpty())
            {
                existingDocument.setDescription(description.trim());
            }
            Document updatedDocument = documentRepository.save(existingDocument);

            log.info("Document {} updated successfully for user {} (file updated: {})",
                    documentId, userId, fileUpdated);

            return documentResponseMapper.toResponseDTO(updatedDocument)
                    .orElseThrow(() -> new DocumentProcessingException("Failed to map updated document to DTO"));
        } catch (Exception e) {
            log.error("Document update failed for user {}: {}", userId, e.getMessage(), e);
            throw new DocumentProcessingException("Failed to update document: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void deleteDocument(Long userId, Integer documentId)
    {
        log.info("Deleting document {} for user {}", documentId, userId);

        documentSecurityService.validateDocumentOwnership(userId, documentId);
        if (!documentSecurityService.canDeleteDocument(userId, documentId))
        {
            throw new UnauthorizedAccessException(
                    String.format("User %d is not authorized to delete document %d", userId, documentId));
        }
        Document document = getDocumentEntityById(documentId);
        documentRepository.delete(document);

        log.info("Document {} deleted successfully for user {}", documentId, userId);
    }

    @Override
    public void deleteDocumentByType(Long userId, DocumentType documentType)
    {
        log.info("Deleting document of type {} for user {}", documentType, userId);
        documentServiceHelper.validateUserId(userId);

        documentRepository.deleteByUserIdAndDocumentType(userId, documentType);
        log.info("Document of type {} deleted successfully for user {}", documentType, userId);
    }

    @Override
    public boolean documentExists(Long userId, DocumentType documentType)
    {
        return documentRepository.existsByUserIdAndDocumentType(userId, documentType);
    }

    @Override
    public long getDocumentCount(Long userId)
    {
        return documentRepository.countByUserId(userId);
    }

    @Override
    public Optional<DocumentMetadata> getDocumentMetadata(Long userId, Integer documentId)
    {
        log.debug("Fetching document metadata {} for user {}", documentId, userId);

        try {
            documentSecurityService.validateDocumentOwnership(userId, documentId);
            Document document = getDocumentEntityById(documentId);
            return Optional.of(DocumentMetadata.from(document));
        } catch (DocumentNotFoundException | UnauthorizedAccessException e)
        {
            log.warn("Cannot fetch document metadata: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public PaginatedDocumentResponseDTO getDocumentsPaginated(Long userId, int page, int size)
    {
        log.debug("Fetching documents for user {} (page: {}, size: {})", userId, page, size);

        documentServiceHelper.validateUserId(userId);
        documentServiceHelper.validatePaginationParameters(page, size, documentProperties.getDatabase().getBatchSize());

        Pageable pageable = PageRequest.of(page, size);
        Page<Document> documentPage = documentRepository.findDocumentsByUserIdWithPagination(userId, pageable);

        return documentResponseMapper.toPaginatedResponseDTO(documentPage);
    }

    @Override
    public DocumentResponseDTO replaceDocument(Long userId, MultipartFile file, DocumentType documentType,
            String description)
    {
        log.info("Replacing document of type {} for user {}", documentType, userId);

        Optional<Document> existingDocument = documentRepository.findByUserIdAndDocumentType(userId, documentType);

        if (existingDocument.isPresent())
        {
            return updateDocument(userId, existingDocument.get().getDocumentId(), file, description);
        } else
        {
            return uploadDocument(userId, file, documentType, description);
        }
    }

    @Override
    @Transactional
    public byte[] downloadDocument(Long userId, Integer documentId)
    {
        log.info("Download request: user={}, documentId={}", userId, documentId);

        documentSecurityService.validateDocumentOwnership(userId, documentId);

        Document document = getDocumentEntityById(documentId);

        if (document.getFileData() == null || document.getFileData().length == 0)
        {
            throw new DocumentProcessingException(
                    String.format("Document %d has no file data", documentId));
        }

        log.debug("Document downloaded: id={}, size={}KB",
                documentId, document.getFileData().length / 1024);

        return document.getFileData();
    }

    private Document getDocumentEntityById(Integer documentId)
    {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found with ID: " + documentId));
    }

    private boolean isCurrentUserAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    /**
     * Admin-only: get all documents for any user by their userId.
     * Validates caller is ADMIN before proceeding.
     */
    public List<DocumentResponseDTO> getAllDocumentsByUserIdForAdmin(Long targetUserId) {
        log.info("Admin fetching all documents for user {}", targetUserId);

        if (!isCurrentUserAdmin()) {
            throw new UnauthorizedAccessException("Only admins can access documents of other users");
        }

        documentServiceHelper.validateUserId(targetUserId);

        List<Document> documents = documentRepository.findByUserIdOrderByUploadedAtDesc(targetUserId);
        log.debug("Found {} documents for user {}", documents.size(), targetUserId);
        return documentResponseMapper.toResponseDTOList(documents);
    }

}
