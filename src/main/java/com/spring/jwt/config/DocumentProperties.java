package com.spring.jwt.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Configuration properties for document management system
 *
 * This class centralizes all configuration related to document management,
 * eliminating hardcoded values and providing type-safe configuration
 * management.
 *
 **/

@Data
@Configuration
@ConfigurationProperties(prefix = "app.document")
@Validated
public class DocumentProperties
{

    /**
     * File size configurations
     */
    @NotNull
    private FileSizeConfig fileSize = new FileSizeConfig();

    /**
     * Image processing configurations
     */
    @NotNull
    private ImageConfig image = new ImageConfig();

    /**
     * PDF processing configurations
     */
    @NotNull
    private PdfConfig pdf = new PdfConfig();

    /**
     * Supported file types
     */
    @NotEmpty
    private List<String> supportedImageTypes = List.of("image/jpeg", "image/jpg", "image/png", "image/webp");

    @NotEmpty
    private List<String> supportedDocumentTypes = List.of("application/pdf");

    /**
     * Database configurations
     */
    @NotNull
    private DatabaseConfig database = new DatabaseConfig();

    @Data
    public static class FileSizeConfig
    {
        @Min(1)
        private long maxFileSizeBytes = 15 * 1024 * 1024; // Global Fallback

        @Min(1)
        private long maxRequestSizeBytes = 20 * 1024 * 1024; // 20MB

        @Min(1)
        private long fileSizeThresholdBytes = 2 * 1024; // 2KB

        @Min(1)
        private long targetFileSizeKb = 1536; // 1.5MB (User requested >1MB & <2MB)
    }

    @Data
    public static class ImageConfig
    {
        @Min(1)
        private long maxInputSizeBytes = 10 * 1024 * 1024; // 10MB

        @Min(1)
        private int maxWidth = 2560;

        @Min(1)
        private int maxHeight = 1080;

        @Min(1)
        private int profilePhotoSize = 800;

        private float highQuality = 0.95f;
        private float mediumQuality = 0.85f;
        private float lowQuality = 0.75f;
        private float qualityStep = 0.05f;

        @Min(1)
        private long maxSizeKb = 400;

        @Min(1)
        private long profilePhotoMaxSizeKb = 200;

        @Min(1)
        private int profilePhotoInputMultiplier = 10;
    }

    @Data
    public static class PdfConfig
    {
        @Min(1)
        private long maxInputSizeBytes = 1 * 1024 * 1024; // 1MB

        @Min(1)
        private int compressionLevel = 9;

        @Min(1)
        private long maxSizeKb = 1024;
    }

    @Data
    public static class DatabaseConfig
    {
        @Min(1)
        private int batchSize = 50;

        private boolean enableLazyLoading = true;
        private boolean enableQueryCache = true;
    }

    /**
     * Utility methods for easy access and validation
     * These methods provide convenient access to configuration values
     * and perform common validation operations
     */

    /**
     * Get maximum file size in megabytes for display purposes
     *
     * @return maximum file size in MB
     */
    public long getMaxFileSizeMB()
    {
        return fileSize.maxFileSizeBytes / (1024 * 1024);
    }

    /**
     * Check if the given content type is a supported image type
     *
     * @param contentType the MIME type to check
     * @return true if the content type is a supported image type
     */
    public boolean isSupportedImageType(String contentType)
    {
        return contentType != null && supportedImageTypes.contains(contentType);
    }

    /**
     * Check if the given content type is a supported document type
     *
     * @param contentType the MIME type to check
     * @return true if the content type is a supported document type
     */
    public boolean isSupportedDocumentType(String contentType)
    {
        return contentType != null && supportedDocumentTypes.contains(contentType);
    }

    /**
     * Check if the given content type is any supported file type
     *
     * @param contentType the MIME type to check
     * @return true if the content type is supported (either image or document)
     */
    public boolean isSupportedFileType(String contentType)
    {
        return isSupportedImageType(contentType) || isSupportedDocumentType(contentType);
    }
}
