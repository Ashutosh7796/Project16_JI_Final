package com.spring.jwt.entity;

import com.spring.jwt.Enums.DocumentType;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
@Data
@Entity
@Table(
        name = "emp_document",
        indexes = {
                @Index(name = "emp_document", columnList = "emp_document_id")
        }
)
public class EmpDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long empDocumentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Lob
    @Column(name = "pdf_url", columnDefinition = "LONGBLOB", nullable = false)
    private byte[] pdfUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private DocumentType documentType;

    private LocalDateTime uploadedAt = LocalDateTime.now();
}
