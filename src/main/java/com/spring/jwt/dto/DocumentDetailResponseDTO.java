package com.spring.jwt.dto;

import com.spring.jwt.Enums.DocumentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DocumentDetailResponseDTO {
    private Integer documentId;
    private DocumentType documentType;
    private String fileName;
    private String description;
    private Long fileSize;
    private String contentType;
    private String fileData;
    private LocalDateTime uploadedAt;
    private LocalDateTime updatedAt;
}