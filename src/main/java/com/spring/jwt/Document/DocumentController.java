package com.spring.jwt.Document;

import com.spring.jwt.Document.domain.DocumentMetadata;
import com.spring.jwt.Enums.DocumentType;
import com.spring.jwt.config.DocumentProperties;
import com.spring.jwt.dto.DocumentDetailResponseDTO;
import com.spring.jwt.dto.DocumentResponseDTO;
import com.spring.jwt.dto.PaginatedDocumentResponseDTO;
import com.spring.jwt.utils.ApiResponse;
import com.spring.jwt.utils.SecurityUtil;import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/documents")
@Slf4j
@RequiredArgsConstructor
@Validated
@Tag(name = "Document Management", description = "APIs for managing user documents including upload, retrieval, update, and deletion")
public class DocumentController
{

        private final DocumentService documentService;
        private final DocumentProperties documentProperties;

        @Operation(summary = "Upload a document", description = "Upload a document file with automatic compression and validation. Supports PDF, JPEG, PNG, and WEBP formats up to 15MB.")
        @ApiResponses(value =
                {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                                description = "Document uploaded successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                                description = "Invalid file or parameters"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413",
                                description = "File size exceeds limit"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                                description = "Unauthorized")
                })
        @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ResponseEntity<ApiResponse<DocumentResponseDTO>> uploadDocument
                (
                        @Parameter(description = "Document file to upload", required = true)
                        @RequestParam("file") MultipartFile file,
                        @Parameter(description = "Type of document being uploaded", required = true)
                        @RequestParam("documentType") DocumentType documentType,
                        @Parameter(description = "Optional description for the document")
                        @RequestParam(value = "description", required = false) String description
                )
        {

                Long userId = SecurityUtil.getCurrentUserId();
                log.info("Upload request: user={}, type={} (enum={}), file={}, size={}KB",
                                userId, documentType, documentType.name(), file.getOriginalFilename(), file.getSize() / 1024);

                DocumentResponseDTO response = documentService.uploadDocument(userId, file, documentType, description);

                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(ApiResponse.success("Document uploaded successfully", response));
        }

        @Operation(summary = "Upload a document", description = "Upload a document file with automatic compression and validation. Supports PDF, JPEG, PNG, and WEBP formats up to 15MB.")
        @ApiResponses(value =
                {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                                description = "Document uploaded successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                                description = "Invalid file or parameters"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413",
                                description = "File size exceeds limit"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                                description = "Unauthorized")
                })
        @PostMapping(value = "/uploadByUser", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ResponseEntity<ApiResponse<DocumentResponseDTO>> uploadDocumentUserId
                (
                        @Parameter(description = "Document file to upload", required = true)
                        @RequestParam("file") MultipartFile file,
                        @Parameter(description = "Type of document being uploaded", required = true)
                        @RequestParam("documentType") DocumentType documentType,
                        @Parameter(description = "Optional description for the document")
                        @RequestParam(value = "description", required = false) String description,
                        @Parameter(description = "userId")
                        @RequestParam("UserId") Long userId
                )
        {

//                Long userId = SecurityUtil.getCurrentUserId();
                log.info("Upload request: user={}, type={} (enum={}), file={}, size={}KB",
                        userId, documentType, documentType.name(), file.getOriginalFilename(), file.getSize() / 1024);

                DocumentResponseDTO response = documentService.uploadDocument(userId, file, documentType, description);

                return ResponseEntity.status(HttpStatus.CREATED)
                        .body(ApiResponse.success("Document uploaded successfully", response));
        }

        /**
         * Replace existing document of the same type or create new
         *
         * Business Logic:
         * 1. Check if document of same type exists for user
         * 2. If exists: update existing document
         * 3. If not exists: create new document
         * 4. Return appropriate response
         */
        @Operation(summary = "Replace existing document", description = "Replace an existing document of the same type or create new if doesn't exist")
        @PostMapping(value = "/replace", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ResponseEntity<ApiResponse<DocumentResponseDTO>> replaceDocument
        (
                        @RequestParam("file") MultipartFile file,
                        @RequestParam("documentType") DocumentType documentType,
                        @RequestParam(value = "description", required = false) String description)
        {

                Long userId = SecurityUtil.getCurrentUserId();
                log.info("Replace request: user={}, type={}", userId, documentType);

                DocumentResponseDTO response = documentService.replaceDocument(userId, file, documentType, description);

                return ResponseEntity.ok(ApiResponse.success("Document replaced successfully", response));
        }

        /**
         * Get document by ID with file data
         *
         * Business Logic:
         * 1. Validate user ownership of document
         * 2. Retrieve document with binary data
         * 3. Convert binary data to Base64 for JSON response
         * 4. Return complete document information
         */
        @Operation(summary = "Get document by ID", description = "Retrieve a specific document by its ID including the file data (Base64 encoded)")
        @GetMapping("/{documentId}")
        public ResponseEntity<ApiResponse<DocumentDetailResponseDTO>> getDocumentById
        (
                        @Parameter(description = "Document ID", required = true)
                        @PathVariable Integer documentId)
        {

                Long userId = SecurityUtil.getCurrentUserId();
                log.debug("Get document request: user={}, documentId={}", userId, documentId);

                DocumentDetailResponseDTO response = documentService.getDocumentById(userId, documentId);

                return ResponseEntity.ok(ApiResponse.success("Document retrieved successfully", response));
        }


        /**
         * Get document metadata without file data for performance
         *
         * Business Logic:
         * 1. Validate user ownership
         * 2. Return only metadata (no binary data)
         * 3. Optimized for performance when file content not needed
         */
        @Operation(summary = "Get document metadata", description = "Retrieve document metadata without the file data for performance")
        @GetMapping("/{documentId}/metadata")
        public ResponseEntity<ApiResponse<DocumentMetadata>> getDocumentMetadata
        (
                        @PathVariable Integer documentId)
        {

                Long userId = SecurityUtil.getCurrentUserId();
                log.debug("Get metadata request: user={}, documentId={}", userId, documentId);

                Optional<DocumentMetadata> metadata = documentService.getDocumentMetadata(userId, documentId);

                if (metadata.isPresent()) {
                        return ResponseEntity.ok(ApiResponse.success("Document metadata retrieved", metadata.get()));
                } else {
                        return ResponseEntity.notFound().build();
                }
        }

        /**
         * Get document by type for current user
         *
         * Business Logic:
         * 1. Find document of specific type for user
         * 2. Return document with file data
         * 3. Useful for retrieving specific document types (e.g., profile photo)
         */
        @Operation(summary = "Get document by type", description = "Retrieve a document by its type for the current user")
        @GetMapping("/type/{documentType}")
        public ResponseEntity<ApiResponse<DocumentDetailResponseDTO>> getDocumentByType
        (
                        @Parameter(description = "Document type", required = true)
                        @PathVariable DocumentType documentType)
        {

                Long userId = SecurityUtil.getCurrentUserId();
                log.debug("Get by type request: user={}, type={}", userId, documentType);

                DocumentDetailResponseDTO response = documentService.getDocumentByType(userId, documentType);

                return ResponseEntity.ok(ApiResponse.success("Document retrieved successfully", response));
        }

        /**
         * Get all documents for current user (without file data for performance)
         *
         * Business Logic:
         * 1. Retrieve all documents for user
         * 2. Return metadata only (no binary data)
         * 3. Ordered by upload date (newest first)
         */
        @Operation(summary = "Get all user documents", description = "Retrieve all documents for the current user (without file data for performance)")
        @GetMapping("/user/all")
        public ResponseEntity<ApiResponse<List<DocumentResponseDTO>>> getAllDocumentsByUserId()
        {

                Long userId = SecurityUtil.getCurrentUserId();
                log.debug("Get all documents request: user={}", userId);

                List<DocumentResponseDTO> response = documentService.getAllDocumentsByUserId(userId);

                return ResponseEntity.ok(ApiResponse.success(
                                String.format("Retrieved %d documents successfully", response.size()), response));
        }

        /**
         * Admin endpoint: get all documents for any user by their userId.
         * Requires ADMIN role.
         */
        @Operation(summary = "Admin: Get all documents for a user", description = "Admin-only endpoint to retrieve all documents for any user by their userId")
        @ApiResponses(value = {
                @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Documents retrieved successfully"),
                @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - Admin role required"),
                @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found")
        })
        @GetMapping("/user/{userId}/all")
        public ResponseEntity<ApiResponse<List<DocumentResponseDTO>>> getAllDocumentsByUserIdForAdmin(
                @Parameter(description = "Target user ID", required = true)
                @PathVariable Long userId)
        {
                log.info("Admin get all documents request: targetUser={}, requestedBy={}",
                        userId, SecurityUtil.getCurrentUserId());

                List<DocumentResponseDTO> response = ((DocumentServiceImpl) documentService)
                        .getAllDocumentsByUserIdForAdmin(userId);

                return ResponseEntity.ok(ApiResponse.success(
                        String.format("Retrieved %d documents for user %d", response.size(), userId), response));
        }

        /**
         * Get documents with pagination support
         *
         * Business Logic:
         * 1. Validate pagination parameters
         * 2. Apply pagination logic
         * 3. Return subset of documents
         * 4. Useful for large document collections
         */
        @Operation(summary = "Get documents with pagination", description = "Retrieve documents for the current user with pagination support")
        @GetMapping("/user/paginated")
        public ResponseEntity<ApiResponse<PaginatedDocumentResponseDTO>> getDocumentsPaginated
        (
                        @Parameter(description = "Page number (0-based)")
                        @RequestParam(defaultValue = "0")
                        @Min(0) int page,
                        @Parameter(description = "Page size (1-50)")
                        @RequestParam(defaultValue = "10")
                        @Min(value = 1, message = "Page size must be at least 1")
                        @Max(value = 30, message = "Page size cannot exceed 30") int size
        )
        {

                Long userId = SecurityUtil.getCurrentUserId();
                log.debug("Paginated request: user={}, page={}, size={}", userId, page, size);

                PaginatedDocumentResponseDTO response = documentService.getDocumentsPaginated(userId, page, size);

                return ResponseEntity.ok(ApiResponse.success(
                                String.format("Retrieved page %d with %d documents (total: %d)",
                                                page, response.getNumberOfElements(), response.getTotalElements()),
                                response));
        }

        /**
         * Get documents by multiple types
         *
         * Business Logic:
         * 1. Accept list of document types
         * 2. Filter documents by specified types
         * 3. Return matching documents
         */
        @Operation(summary = "Get documents by types", description = "Retrieve documents of specific types for the current user")
        @PostMapping("/user/by-types")
        public ResponseEntity<ApiResponse<List<DocumentResponseDTO>>> getDocumentsByTypes
        (
                        @Parameter(description = "List of document types to retrieve")
                        @RequestBody List<DocumentType> documentTypes
        )
        {

                Long userId = SecurityUtil.getCurrentUserId();
                log.debug("Get by types request: user={}, types={}", userId, documentTypes);

                List<DocumentResponseDTO> response = documentService.getDocumentsByTypes(userId, documentTypes);

                return ResponseEntity.ok(ApiResponse.success("Documents retrieved successfully", response));
        }

        /**
         * Update existing document
         *
         * Business Logic:
         * 1. Validate user ownership and permissions
         * 2. Update file if provided (with reprocessing)
         * 3. Update description if provided
         * 4. Save changes and return updated document
         */
        @Operation(summary = "Update document", description = "Update an existing document's file and/or description")
        @PutMapping(value = "/{documentId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ResponseEntity<ApiResponse<DocumentResponseDTO>> updateDocument
        (
                        @Parameter(description = "Document ID to update", required = true)
                        @PathVariable Integer documentId,
                        @Parameter(description = "New file (optional)")
                        @RequestParam(value = "file", required = false) MultipartFile file,
                        @Parameter(description = "New description (optional)")
                        @RequestParam(value = "description", required = false) String description
        )
        {

                Long userId = SecurityUtil.getCurrentUserId();
                log.info("Update request: user={}, documentId={}, hasFile={}",
                                userId, documentId, file != null && !file.isEmpty());

                DocumentResponseDTO response = documentService.updateDocument(userId, documentId, file, description);

                return ResponseEntity.ok(ApiResponse.success("Document updated successfully", response));
        }

        /**
         * Delete document by ID
         *
         * Business Logic:
         * 1. Validate user ownership and delete permissions
         * 2. Remove document from database
         * 3. Clean up associated resources
         */
        @Operation(summary = "Delete document by ID", description = "Delete a specific document by its ID")
        @DeleteMapping("/{documentId}")
        public ResponseEntity<ApiResponse<Void>> deleteDocument
        (
                        @Parameter(description = "Document ID to delete", required = true)
                        @PathVariable Integer documentId
        )
        {

                Long userId = SecurityUtil.getCurrentUserId();
                log.info("Delete request: user={}, documentId={}", userId, documentId);

                documentService.deleteDocument(userId, documentId);

                return ResponseEntity.ok(ApiResponse.success("Document deleted successfully", null));
        }

        /**
         * Delete document by type
         *
         * Business Logic:
         * 1. Find document of specified type for user
         * 2. Delete if exists
         * 3. Useful for replacing document types
         */
        @Operation(summary = "Delete document by type", description = "Delete a document by its type for the current user")
        @DeleteMapping("/type/{documentType}")
        public ResponseEntity<ApiResponse<Void>> deleteDocumentByType
        (
                        @Parameter(description = "Document type to delete", required = true)
                        @PathVariable DocumentType documentType
        )
        {

                Long userId = SecurityUtil.getCurrentUserId();
                log.info("Delete by type request: user={}, type={}", userId, documentType);

                documentService.deleteDocumentByType(userId, documentType);

                return ResponseEntity.ok(ApiResponse.success("Document deleted successfully", null));
        }

        /**
         * Check if document exists for a type
         *
         * Business Logic:
         * 1. Query database for document existence
         * 2. Return boolean result
         * 3. Useful for UI logic and validation
         */
        @Operation(summary = "Check document existence", description = "Check if a document of specific type exists for the current user")
        @GetMapping("/exists/{documentType}")
        public ResponseEntity<ApiResponse<Boolean>> checkDocumentExists
        (
                        @Parameter(description = "Document type to check", required = true)
                        @PathVariable DocumentType documentType)
        {

                Long userId = SecurityUtil.getCurrentUserId();
                log.debug("Existence check: user={}, type={}", userId, documentType);

                boolean exists = documentService.documentExists(userId, documentType);

                return ResponseEntity.ok(ApiResponse.success("Document existence checked", exists));
        }

        /**
         * Get document count for user
         *
         * Business Logic:
         * 1. Count total documents for user
         * 2. Return numeric count
         * 3. Useful for dashboard and statistics
         */
        @Operation(summary = "Get document count", description = "Get the total number of documents for the current user")
        @GetMapping("/count")
        public ResponseEntity<ApiResponse<Long>> getDocumentCount()
        {

                Long userId = SecurityUtil.getCurrentUserId();
                log.debug("Count request: user={}", userId);

                long count = documentService.getDocumentCount(userId);

                return ResponseEntity.ok(ApiResponse.success("Document count retrieved", count));
        }

        /**
         * Get available document types
         *
         * Business Logic:
         * 1. Return all available document types from enum
         * 2. Useful for UI dropdowns and validation
         */
        @Operation(summary = "Get available document types", description = "Retrieve all available document types that can be uploaded")
        @GetMapping("/types")
        public ResponseEntity<ApiResponse<DocumentType[]>> getDocumentTypes()
        {

                log.debug("Document types request");

                return ResponseEntity.ok(ApiResponse.success("Document types retrieved", DocumentType.values()));
        }

        /**
         * Get system configuration information
         *
         * Business Logic:
         * 1. Return system configuration for file uploads
         * 2. Include size limits, supported types, etc.
         * 3. Useful for client-side validation and UI
         */
        @Operation(summary = "Get upload configuration", description = "Get system configuration for file uploads including size limits and supported types")
        @GetMapping("/config")
        public ResponseEntity<ApiResponse<Object>> getUploadConfig()
        {

                log.debug("Config request");

                var config = new Object()
                {
                        public final long maxFileSizeBytes = documentProperties.getFileSize().getMaxFileSizeBytes();
                        public final long maxFileSizeMB = documentProperties.getMaxFileSizeMB();
                        public final List<String> supportedImageTypes = documentProperties.getSupportedImageTypes();
                        public final List<String> supportedDocumentTypes = documentProperties
                                        .getSupportedDocumentTypes();
                        public final DocumentType[] availableDocumentTypes = DocumentType.values();
                };

                return ResponseEntity.ok(ApiResponse.success("Upload configuration retrieved", config));
        }
}