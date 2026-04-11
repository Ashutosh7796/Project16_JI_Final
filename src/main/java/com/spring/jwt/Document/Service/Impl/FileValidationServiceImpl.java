package com.spring.jwt.Document.Service.Impl;

import com.spring.jwt.Document.Service.FileValidationService;
import com.spring.jwt.Enums.DocumentType;
import com.spring.jwt.config.DocumentProperties;
import com.spring.jwt.exception.InvalidDocumentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Objects;

/**
 * Implementation of file validation service
 * Provides comprehensive file validation with configurable rules
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FileValidationServiceImpl implements FileValidationService
{

    private final DocumentProperties documentProperties;

    @Override
    public void validateFile(MultipartFile file)
    {
        log.debug("Validating file: {}", file.getOriginalFilename());

        validateFileNotNull(file);
        validateFileNotEmpty(file);
        validateFileSize(file);
        validateContentType(file);

        log.debug("File validation successful for: {}", file.getOriginalFilename());
    }

    @Override
    public void validateFileForDocumentType(MultipartFile file, DocumentType documentType)
    {
        validateFile(file);
        validateDocumentTypeSpecificRules(file, documentType);
    }

    @Override
    public boolean isSupportedFileType(String contentType)
    {
        if (contentType == null) {
            return false;
        }
        return documentProperties.isSupportedFileType(contentType);
    }

    @Override
    public boolean isFileSizeValid(long fileSize)
    {
        return fileSize > 0 && fileSize <= documentProperties.getFileSize().getMaxFileSizeBytes();
    }

    @Override
    public long getMaxFileSizeBytes()
    {
        return documentProperties.getFileSize().getMaxFileSizeBytes();
    }

    private void validateFileNotNull(MultipartFile file)
    {
        if (file == null)
        {
            throw new InvalidDocumentException("File cannot be null");
        }
    }

    private void validateFileNotEmpty(MultipartFile file)
    {
        if (file.isEmpty())
        {
            throw new InvalidDocumentException("File cannot be empty");
        }
    }

    private void validateFileSize(MultipartFile file)
    {
        long fileSize = file.getSize();
        long maxSize = documentProperties.getFileSize().getMaxFileSizeBytes();

        if (fileSize <= 0)
        {
            throw new InvalidDocumentException("File size must be greater than 0");
        }

        if (fileSize > maxSize)
        {
            throw new InvalidDocumentException(
                    String.format("File size (%d bytes) exceeds maximum allowed size (%d bytes / %dMB)",
                            fileSize, maxSize, documentProperties.getMaxFileSizeMB()));
        }
    }

    private void validateContentType(MultipartFile file)
    {
        String contentType = file.getContentType();

        if (contentType == null || contentType.trim().isEmpty())
        {
            throw new InvalidDocumentException("File content type cannot be null or empty");
        }

        if (!isSupportedFileType(contentType))
        {
            throw new InvalidDocumentException(
                    String.format("Unsupported file type: %s. Supported types: %s, %s",
                            contentType,
                            String.join(", ", documentProperties.getSupportedImageTypes()),
                            String.join(", ", documentProperties.getSupportedDocumentTypes())));
        }
    }

    private void validateDocumentTypeSpecificRules(MultipartFile file, DocumentType documentType)
    {
        String contentType = file.getContentType();

        switch (documentType)
        {
            case PROFILE_PHOTO:
                validateProfilePhotoRules(file, contentType);
                break;
            case AADHAAR_CARD:
            case PAN_CARD:
            case PASSPORT:
                validateIdentityDocumentRules(file, contentType);
                break;
            case RESUME:
                validateResumeRules(file, contentType);
                break;
            default:
                break;
        }
    }

    private void validateProfilePhotoRules(MultipartFile file, String contentType)
    {
        if (!documentProperties.isSupportedImageType(contentType))
        {
            throw new InvalidDocumentException("Profile photo must be an image file (JPEG, PNG, WEBP)");
        }

        long maxProfilePhotoSize = documentProperties.getImage().getProfilePhotoMaxSizeKb() * 1024;
        int inputMultiplier = documentProperties.getImage().getProfilePhotoInputMultiplier();

        if (file.getSize() > maxProfilePhotoSize * inputMultiplier)
        {
            throw new InvalidDocumentException(
                    String.format("Profile photo is too large. Maximum recommended size before compression: %dMB",
                            (maxProfilePhotoSize * inputMultiplier) / (1024 * 1024)));
        }
    }

    private void validateIdentityDocumentRules(MultipartFile file, String contentType)
    {
        if (!documentProperties.isSupportedFileType(contentType)) {
            throw new InvalidDocumentException("Identity documents must be PDF or image files");
        }
    }

    private void validateResumeRules(MultipartFile file, String contentType)
    {

        if (!Objects.equals(contentType, "application/pdf") &&
                !documentProperties.isSupportedImageType(contentType)) {
            throw new InvalidDocumentException("Resume should be a PDF file or image");
        }
    }
}