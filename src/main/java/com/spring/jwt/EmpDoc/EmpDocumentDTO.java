package com.spring.jwt.EmpDoc;

import com.spring.jwt.Enums.DocumentType;
import lombok.Data;

@Data
public class EmpDocumentDTO {
    private Long empDocumentId;
    private Long userId;
    private byte[] pdfUrl;
    private DocumentType documentType;
}
