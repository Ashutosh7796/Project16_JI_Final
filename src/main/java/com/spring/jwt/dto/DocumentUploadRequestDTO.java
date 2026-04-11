package com.spring.jwt.dto;

import com.spring.jwt.Enums.DocumentType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DocumentUploadRequestDTO {
    
    @NotNull(message = "Document type is required")
    private DocumentType documentType;
    
    private String description;
}