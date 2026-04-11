package com.spring.jwt.Document.Service.Impl;

import com.spring.jwt.Document.Service.FileProcessingService;
import com.spring.jwt.utils.ImageOptimizationService;
import com.spring.jwt.Document.domain.FileProcessingResult;
import com.spring.jwt.Enums.DocumentType;
import com.spring.jwt.config.DocumentProperties;
import com.spring.jwt.exception.DocumentProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Optimized file processing service
 * Target: < 1 second processing time
 */
@Service
@Primary
@Slf4j
@RequiredArgsConstructor
public class OptimizedFileProcessingServiceImpl implements FileProcessingService
{

    private final DocumentProperties documentProperties;
    private final ImageOptimizationService imageOptimizationService;

    @Override
    public CompletableFuture<FileProcessingResult> processFile(MultipartFile file, DocumentType documentType)
    {
        try {
            long startTime = System.currentTimeMillis();

            byte[] originalData = file.getBytes();
            String contentType = file.getContentType();
            long originalSize = originalData.length;

            if ("application/pdf".equals(contentType))
            {
                if (originalSize > documentProperties.getPdf().getMaxInputSizeBytes())
                {
                    throw new DocumentProcessingException("PDF file size exceeds maximum limit of " +
                            (documentProperties.getPdf().getMaxInputSizeBytes() / 1024 / 1024) + "MB");
                }
            } else if (contentType != null && contentType.startsWith("image/"))
            {
                if (originalSize > documentProperties.getImage().getMaxInputSizeBytes())
                {
                    throw new DocumentProcessingException("Image file size exceeds maximum limit of " +
                            (documentProperties.getImage().getMaxInputSizeBytes() / 1024 / 1024) + "MB");
                }
            }

            if (!imageOptimizationService.needsProcessing(originalSize, contentType))
            {
                log.info("File {} is small enough according to configuration, skipping processing",
                        file.getOriginalFilename());

                FileProcessingResult result = FileProcessingResult.builder()
                        .processedData(originalData)
                        .originalSize(originalSize)
                        .processedSize(originalSize)
                        .processingType("NO_PROCESSING")
                        .processingDetails("File size acceptable, processing skipped for performance")
                        .build();

                return CompletableFuture.completedFuture(result);
            }

            FileProcessingResult result;

            if (documentProperties.isSupportedFileType(contentType))
            {
                byte[] processedData = imageOptimizationService.processDocument(originalData, contentType,
                        documentType.name());
                long processedSize = processedData.length;

                result = FileProcessingResult.builder()
                        .processedData(processedData)
                        .originalSize(originalSize)
                        .processedSize(processedSize)
                        .processingType("UNIFIED_OPTIMIZATION")
                        .processingDetails(String.format("Smart compression (Target: %dKB)",
                                documentProperties.getFileSize().getTargetFileSizeKb()))
                        .build();
            } else
            {
                throw new DocumentProcessingException("Unsupported file type: " + contentType);
            }

            long processingTime = System.currentTimeMillis() - startTime;
            log.info("Optimized file processing completed in {}ms: {}", processingTime, result.getProcessingSummary());

            return CompletableFuture.completedFuture(result);

        } catch (IOException e)
        {
            log.error("Optimized processing failed for file: {}", file.getOriginalFilename(), e);
            throw new DocumentProcessingException("Optimized processing failed: " + e.getMessage(), e);
        }
    }

    @Override
    public CompletableFuture<FileProcessingResult> processImage(byte[] fileData, DocumentType documentType)
    {
        try {
            FileProcessingResult result = processImageOptimized(fileData, documentType);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e)
        {
            log.error("Optimized image processing failed for document type: {}", documentType, e);
            throw new DocumentProcessingException("Optimized image processing failed: " + e.getMessage(), e);
        }
    }

    @Override
    public CompletableFuture<FileProcessingResult> processPdf(byte[] fileData)
    {
        try {
            FileProcessingResult result = processPdfOptimized(fileData);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e)
        {
            log.error("Optimized PDF processing failed", e);
            throw new DocumentProcessingException("Optimized PDF processing failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String getProcessingStrategy(DocumentType documentType)
    {
        return "OPTIMIZED_PROCESSING";
    }


    private FileProcessingResult processImageOptimized(byte[] originalData, DocumentType documentType)
            throws IOException
    {
        long originalSize = originalData.length;

        byte[] processedData = imageOptimizationService.compressImage(originalData, documentType.name());
        long processedSize = processedData.length;

        return FileProcessingResult.builder()
                .processedData(processedData)
                .originalSize(originalSize)
                .processedSize(processedSize)
                .processingType("OPTIMIZED_IMAGE_COMPRESSION")
                .processingDetails(String.format("Optimized compression for %s using configured quality", documentType))
                .build();
    }

    private FileProcessingResult processPdfOptimized(byte[] originalData) throws IOException
    {
        // IOException allowed to propagate or caught inside? The original wrapper
        // caught it.
        // Let's keep the try-catch wrapper in the caller or here?
        // The original logic caught IOException and threw DocumentProcessingException.
        // The replace block below replaces the WHOLE method.

        try {
            long originalSize = originalData.length;

            byte[] processedData = imageOptimizationService.compressPdf(originalData);

            long processedSize = processedData.length;

            return FileProcessingResult.builder()
                    .processedData(processedData)
                    .originalSize(originalSize)
                    .processedSize(processedSize)
                    .processingType("OPTIMIZED_PDF_PROCESSING")
                    .processingDetails(String.format("Optimized PDF processing, size: %dKB", processedSize / 1024))
                    .build();

        } catch (IOException e)
        {
            throw new DocumentProcessingException("Optimized PDF processing failed", e);
        }
    }
}
