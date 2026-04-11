package com.spring.jwt.mapper;

import com.spring.jwt.dto.DocumentDetailResponseDTO;
import com.spring.jwt.dto.DocumentResponseDTO;
import com.spring.jwt.dto.PaginatedDocumentResponseDTO;
import com.spring.jwt.entity.Document;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class DocumentResponseMapper {

    /**
     * Convert Document entity to DocumentResponseDTO (without file data)
     * 
     * @return Optional containing the DTO, or empty if document is null
     */
    public Optional<DocumentResponseDTO> toResponseDTO(Document document) {
        return Optional.ofNullable(document)
                .map(doc -> DocumentResponseDTO.builder()
                        .documentId(doc.getDocumentId())
                        .documentType(doc.getDocumentType())
                        .fileName(doc.getFileName())
                        .description(doc.getDescription())
                        .fileSize(doc.getFileSize())
                        .contentType(doc.getContentType())
                        .uploadedAt(doc.getUploadedAt())
                        .updatedAt(doc.getUpdatedAt())
                        .build());
    }

    /**
     * Convert Document entity to DocumentDetailResponseDTO (with file data)
     * 
     * @return Optional containing the detail DTO, or empty if document is null
     */
    public Optional<DocumentDetailResponseDTO> toDetailResponseDTO(Document document) {
        return Optional.ofNullable(document)
                .map(doc -> {
                    String base64FileData = doc.getFileData() != null
                            ? Base64.getEncoder().encodeToString(doc.getFileData())
                            : null;

                    return DocumentDetailResponseDTO.builder()
                            .documentId(doc.getDocumentId())
                            .documentType(doc.getDocumentType())
                            .fileName(doc.getFileName())
                            .description(doc.getDescription())
                            .fileSize(doc.getFileSize())
                            .contentType(doc.getContentType())
                            .fileData(base64FileData)
                            .uploadedAt(doc.getUploadedAt())
                            .updatedAt(doc.getUpdatedAt())
                            .build();
                });
    }

    /**
     * Convert list of Document entities to list of DocumentResponseDTOs
     * Uses streams and flatMap to filter out null documents
     */
    public List<DocumentResponseDTO> toResponseDTOList(List<Document> documents) {
        return Optional.ofNullable(documents)
                .map(docs -> docs.stream()
                        .map(this::toResponseDTO)
                        .flatMap(Optional::stream)
                        .collect(Collectors.toList()))
                .orElse(List.of());
    }

    /**
     * Convert Spring Data Page to PaginatedDocumentResponseDTO
     * Centralizes pagination metadata mapping following Single Responsibility
     * Principle
     * 
     * @param documentPage Spring Data Page containing documents
     * @return Paginated response DTO with all pagination metadata
     */
    public PaginatedDocumentResponseDTO toPaginatedResponseDTO(
            org.springframework.data.domain.Page<Document> documentPage) {
        List<DocumentResponseDTO> documents = toResponseDTOList(documentPage.getContent());

        return PaginatedDocumentResponseDTO.builder()
                .documents(documents)
                .currentPage(documentPage.getNumber())
                .pageSize(documentPage.getSize())
                .totalElements(documentPage.getTotalElements())
                .totalPages(documentPage.getTotalPages())
                .first(documentPage.isFirst())
                .last(documentPage.isLast())
                .hasNext(documentPage.hasNext())
                .hasPrevious(documentPage.hasPrevious())
                .numberOfElements(documentPage.getNumberOfElements())
                .empty(documentPage.isEmpty())
                .build();
    }

    /**
     * Legacy method for backward compatibility
     * 
     * @deprecated Use {@link #toResponseDTO(Document)} instead
     */
    @Deprecated
    public Optional<DocumentResponseDTO> toDTO(Document document) {
        return toResponseDTO(document);
    }
}
