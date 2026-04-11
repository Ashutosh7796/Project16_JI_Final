package com.spring.jwt.entity;

import com.spring.jwt.Enums.DocumentType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "document",
        indexes = {
                @Index(name = "idx_user_document_type", columnList = "user_id, document_type"),
                @Index(name = "idx_user_id", columnList = "user_id")
        })
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer documentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 100)
    private DocumentType documentType;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private Long fileSize;

    @Column(nullable = false, length = 100)
    private String contentType;

    @Lob
    @Column(columnDefinition = "MEDIUMBLOB", nullable = false)
    private byte[] fileData;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

}