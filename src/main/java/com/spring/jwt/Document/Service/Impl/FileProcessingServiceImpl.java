package com.spring.jwt.Document.Service.Impl;

import com.spring.jwt.Document.Service.FileProcessingService;
import com.spring.jwt.Document.domain.FileProcessingResult;
import com.spring.jwt.Enums.DocumentType;
import com.spring.jwt.config.DocumentProperties;
import com.spring.jwt.exception.DocumentProcessingException;
import com.spring.jwt.utils.ImageOptimizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of file processing service
 * Handles file compression and optimization using strategy pattern
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FileProcessingServiceImpl implements FileProcessingService {

    private final DocumentProperties documentProperties;
    private final ImageOptimizationService imageOptimizationService;

    @Override
    @Async("documentProcessingExecutor")
    public CompletableFuture<FileProcessingResult> processFile(MultipartFile file, DocumentType documentType) {
        try {
            log.debug("Processing file: {} for document type: {}", file.getOriginalFilename(), documentType);

            byte[] originalData = file.getBytes();
            String contentType = file.getContentType();

            FileProcessingResult result;

            if (documentProperties.isSupportedFileType(contentType)) {
                byte[] processedData = imageOptimizationService.processDocument(originalData, contentType,
                        documentType.name());
                long processedSize = processedData.length;

                result = FileProcessingResult.builder()
                        .processedData(processedData)
                        .originalSize((long) originalData.length)
                        .processedSize(processedSize)
                        .processingType("UNIFIED_COMPRESSION")
                        .processingDetails("Stratgey: SMART_COMPRESSION")
                        .build();
            } else {
                throw new DocumentProcessingException("Unsupported file type for processing: " + contentType);
            }

            log.info("File processing completed: {}", result.getProcessingSummary());
            return CompletableFuture.completedFuture(result);

        } catch (IOException e) {
            log.error("Failed to process file: {}", file.getOriginalFilename(), e);
            throw new DocumentProcessingException("Failed to process file: " + e.getMessage(), e);
        }
    }

    @Override
    @Async("documentProcessingExecutor")
    public CompletableFuture<FileProcessingResult> processImage(byte[] fileData, DocumentType documentType) {
        try {
            FileProcessingResult result = processImageSync(fileData, documentType);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            log.error("Failed to process image for document type: {}", documentType, e);
            throw new DocumentProcessingException("Failed to process image: " + e.getMessage(), e);
        }
    }

    @Override
    @Async("documentProcessingExecutor")
    public CompletableFuture<FileProcessingResult> processPdf(byte[] fileData) {
        try {
            FileProcessingResult result = processPdfSync(fileData);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            log.error("Failed to process PDF", e);
            throw new DocumentProcessingException("Failed to process PDF: " + e.getMessage(), e);
        }
    }

    @Override
    public String getProcessingStrategy(DocumentType documentType) {
        return switch (documentType) {
            case PROFILE_PHOTO -> "HIGH_QUALITY_COMPRESSION";
            case AADHAAR_CARD, PAN_CARD, PASSPORT -> "DOCUMENT_OPTIMIZATION";
            case RESUME -> "PDF_COMPRESSION";
            default -> "STANDARD_COMPRESSION";
        };
    }

    private FileProcessingResult processImageSync(byte[] originalData, DocumentType documentType) {
        try {
            long originalSize = originalData.length;
            String strategy = getProcessingStrategy(documentType);

            byte[] processedData = imageOptimizationService.compressImage(originalData, documentType.name());
            long processedSize = processedData.length;

            return FileProcessingResult.builder()
                    .processedData(processedData)
                    .originalSize(originalSize)
                    .processedSize(processedSize)
                    .processingType("IMAGE_COMPRESSION")
                    .processingDetails(String.format("Strategy: %s, Quality optimized for %s",
                            strategy, documentType))
                    .build();

        } catch (IOException e) {
            throw new DocumentProcessingException("Image processing failed", e);
        }
    }

    private FileProcessingResult processPdfSync(byte[] originalData) {
        try {
            long originalSize = originalData.length;

            byte[] processedData = imageOptimizationService.compressPdf(originalData);
            long processedSize = processedData.length;

            return FileProcessingResult.builder()
                    .processedData(processedData)
                    .originalSize(originalSize)
                    .processedSize(processedSize)
                    .processingType("PDF_COMPRESSION")
                    .processingDetails(String.format("Compression level: %d",
                            documentProperties.getPdf().getCompressionLevel()))
                    .build();

        } catch (IOException e) {
            throw new DocumentProcessingException("PDF processing failed", e);
        }
    }
}