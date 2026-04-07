package com.spring.jwt.Document.Service;

import com.spring.jwt.Document.domain.FileProcessingResult;
import com.spring.jwt.Enums.DocumentType;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.CompletableFuture;

/**
 * Service interface for file processing operations
 * Handles compression, optimization, and format conversion
 */
public interface FileProcessingService {

    /**
     * Process file based on its type and document type
     * 
     * @param file the uploaded file
     * @param documentType the target document type
     * @return CompletableFuture containing processing result
     */
    CompletableFuture<FileProcessingResult> processFile(MultipartFile file, DocumentType documentType);

    /**
     * Process image file with compression and optimization
     * 
     * @param fileData the image file data
     * @param documentType the target document type
     * @return CompletableFuture containing processing result
     */
    CompletableFuture<FileProcessingResult> processImage(byte[] fileData, DocumentType documentType);

    /**
     * Process PDF file with compression
     * 
     * @param fileData the PDF file data
     * @return CompletableFuture containing processing result
     */
    CompletableFuture<FileProcessingResult> processPdf(byte[] fileData);

    /**
     * Get optimal processing strategy for document type
     * 
     * @param documentType the document type
     * @return processing strategy name
     */
    String getProcessingStrategy(DocumentType documentType);
}