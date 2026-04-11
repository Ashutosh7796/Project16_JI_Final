package com.spring.jwt.Document.domain;

import com.spring.jwt.Enums.DocumentType;
import com.spring.jwt.entity.Document;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

import java.time.LocalDateTime;

@Data
@Builder
public class DocumentMetadata {

    @NonNull
    private final Integer documentId;

    @NonNull
    private final DocumentType documentType;

    @NonNull
    private final String fileName;

    private final String description;

    @NonNull
    private final Long fileSize;

    @NonNull
    private final String contentType;

    @NonNull
    private final Long userId;

    @NonNull
    private final LocalDateTime uploadedAt;

    @NonNull
    private final LocalDateTime updatedAt;

    /**
     * Factory method to create metadata from entity
     */
    public static DocumentMetadata from(Document document)
    {
        return DocumentMetadata.builder()
                .documentId(document.getDocumentId())
                .documentType(document.getDocumentType())
                .fileName(document.getFileName())
                .description(document.getDescription())
                .fileSize(document.getFileSize())
                .contentType(document.getContentType())
                .userId(document.getUser().getUserId())
                .uploadedAt(document.getUploadedAt())
                .updatedAt(document.getUpdatedAt())
                .build();
    }

    /**
     * Check if the document is an image file
     *
     * @return true if the document is an image (JPEG, PNG, WEBP, etc.)
     */
    public boolean isImage()
    {
        return contentType != null && contentType.startsWith("image/");
    }

    /**
     * Check if the document is a PDF file
     *
     * @return true if the document is a PDF
     */
    public boolean isPdf()
    {
        return "application/pdf".equals(contentType);
    }

    /**
     * Check if the document is a profile photo
     *
     * @return true if the document type is PROFILE_PHOTO
     */
    public boolean isProfilePhoto()
    {
        return DocumentType.PROFILE_PHOTO.equals(documentType);
    }

    /**
     * Check if the document is an identity document
     *
     * @return true if the document is an identity document (Aadhaar, PAN, Passport, etc.)
     */
    public boolean isIdentityDocument()
    {
        return documentType == DocumentType.AADHAAR_CARD ||
                documentType == DocumentType.PAN_CARD ||
                documentType == DocumentType.PASSPORT ||
                documentType == DocumentType.DRIVING_LICENSE ||
                documentType == DocumentType.VOTER_ID;
    }

    public String getFileSizeFormatted()
    {
        if (fileSize == null)
        {
            return "Unknown";
        }

        if (fileSize < 1024)
        {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.1f KB", fileSize / 1024.0);
        } else {
            return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
        }
    }

    /**
     * Get file extension from filename
     *
     * @return file extension (e.g., "pdf", "jpg") or empty string if no extension
     */
    public String getFileExtension()
    {
        if (fileName == null || !fileName.contains("."))
        {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }

    /**
     * Check if the document was recently uploaded (within last 24 hours)
     *
     * @return true if uploaded within last 24 hours
     */
    public boolean isRecentlyUploaded()
    {
        if (uploadedAt == null) {
            return false;
        }
        return uploadedAt.isAfter(LocalDateTime.now().minusDays(1));
    }
}