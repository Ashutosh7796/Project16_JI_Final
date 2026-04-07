package com.spring.jwt.utils;

import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfStream;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.WriterProperties;
import com.itextpdf.kernel.pdf.xobject.PdfImageXObject;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.spring.jwt.config.DocumentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Service for optimized image and PDF processing.
 * Utilizes DocumentProperties for centralized configuration.
 * Adheres to industrial standards and OOP principles.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ImageOptimizationService {

    private final DocumentProperties documentProperties;

    static {
        ImageIO.setUseCache(false);
    }

    /**
     * Unified method to process documents (Images & PDFs) to a target size
     */
    public byte[] processDocument(byte[] fileData, String contentType, String documentType) throws IOException {
        long targetSizeBytes = documentProperties.getFileSize().getTargetFileSizeKb() * 1024;

        log.info("TIMING-START-DOC: Processing document type {}, size {}KB", documentType, fileData.length / 1024);

        if (fileData.length <= targetSizeBytes) {
            log.info("TIMING-SKIP: File size {}KB is already within target {}KB", fileData.length / 1024,
                    targetSizeBytes / 1024);
            return fileData;
        }

        if (contentType != null && contentType.startsWith("image/")) {
            return compressImageIteratively(fileData, documentType, targetSizeBytes);
        } else if ("application/pdf".equals(contentType)) {
            return compressPdf(fileData);
        }

        return fileData;
    }

    /**
     * Optimized image compression V5: Source Subsampling + Efficient Resizing +
     * TIMING LOGS
     */
    private byte[] compressImageIteratively(byte[] originalImageBytes, String documentType, long targetSizeBytes)
            throws IOException {
        long startTime = System.currentTimeMillis();

        if (originalImageBytes == null || originalImageBytes.length == 0)
            return originalImageBytes;

        int maxWidth = "PROFILE_PHOTO".equalsIgnoreCase(documentType)
                ? documentProperties.getImage().getProfilePhotoSize()
                : documentProperties.getImage().getMaxWidth();
        int maxHeight = "PROFILE_PHOTO".equalsIgnoreCase(documentType)
                ? documentProperties.getImage().getProfilePhotoSize()
                : documentProperties.getImage().getMaxHeight();

        long decodeStart = System.currentTimeMillis();
        BufferedImage currentImage = decodeCompressed(originalImageBytes, maxWidth, maxHeight);
        long decodeTime = System.currentTimeMillis() - decodeStart;

        if (currentImage == null) {
            log.warn("Failed to decode image efficiently, falling back to original");
            return originalImageBytes;
        }

        log.info("TIMING-DECODE: Decoded in {}ms. Resulting size: {}x{}", decodeTime, currentImage.getWidth(),
                currentImage.getHeight());

        int currentWidth = currentImage.getWidth();

        byte[] compressedBytes = originalImageBytes;
        // User requested > 1.3MB. Starting at 0.95 (High Quality).
        float quality = 0.95f;

        long compressStart = System.currentTimeMillis();
        byte[] fastAttempt = compressWithFixedQuality(currentImage, quality);
        long compressTime = System.currentTimeMillis() - compressStart;
        log.info("TIMING-COMPRESS-1: First pass (Q={}) took {}ms. Size: {}KB", quality, compressTime,
                fastAttempt.length / 1024);

        if (fastAttempt.length <= targetSizeBytes) {
            log.info("TIMING-TOTAL: Fnished in {}ms", System.currentTimeMillis() - startTime);
            return fastAttempt;
        }

        // Attempt 2: Drop to 0.85 (Medium) instead of drastic 0.70
        quality = 0.85f;
        currentImage = Thumbnails.of(currentImage).scale(0.9).asBufferedImage();

        compressStart = System.currentTimeMillis();
        compressedBytes = compressWithFixedQuality(currentImage, quality);
        log.info("TIMING-COMPRESS-2: Second pass (Q={}, Scale=0.9) took {}ms. Size: {}KB", quality,
                System.currentTimeMillis() - compressStart, compressedBytes.length / 1024);

        if (compressedBytes.length > targetSizeBytes) {
            quality = 0.60f;
            currentImage = Thumbnails.of(currentImage).scale(0.9).asBufferedImage();
            compressStart = System.currentTimeMillis();
            byte[] lastAttempt = compressWithFixedQuality(currentImage, quality);
            log.info("TIMING-COMPRESS-3: Third pass (Q={}, Scale=0.9) took {}ms. Size: {}KB", quality,
                    System.currentTimeMillis() - compressStart, lastAttempt.length / 1024);

            if (lastAttempt.length < compressedBytes.length) {
                compressedBytes = lastAttempt;
            }
        }

        log.info("TIMING-TOTAL: Loop finished in {}ms", System.currentTimeMillis() - startTime);
        return compressedBytes;
    }

    private BufferedImage decodeCompressed(byte[] imageBytes, int maxWidth, int maxHeight) throws IOException {
        long start = System.currentTimeMillis();
        try (ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes);
                javax.imageio.stream.ImageInputStream iis = ImageIO.createImageInputStream(bis)) {

            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext())
                return null;

            ImageReader reader = readers.next();
            reader.setInput(iis);

            int originalWidth = reader.getWidth(0);
            int originalHeight = reader.getHeight(0);

            int sampleWidth = originalWidth / maxWidth;
            int sampleHeight = originalHeight / maxHeight;
            int subsampling = Math.max(1, Math.max(sampleWidth, sampleHeight));

            ImageReadParam param = reader.getDefaultReadParam();
            if (subsampling > 1) {
                log.info("TIMING-SUBSAMPLE: Using subsampling {} for {}x{}", subsampling, originalWidth,
                        originalHeight);
                param.setSourceSubsampling(subsampling, subsampling, 0, 0);
            }

            BufferedImage result = reader.read(0, param);
            log.info("TIMING-READ: decodeCompressed took {}ms", System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            log.warn("Subsampling decode failed after {}ms, trying standard read", System.currentTimeMillis() - start,
                    e);
            return ImageIO.read(new ByteArrayInputStream(imageBytes));
        }
    }

    /**
     * Simple one-shot compression (Legacy support / fallback)
     */
    public byte[] compressImage(byte[] originalImageBytes, String documentType) throws IOException {
        long targetSizeBytes = documentProperties.getFileSize().getTargetFileSizeKb() * 1024;
        return compressImageIteratively(originalImageBytes, documentType, targetSizeBytes);
    }

    private byte[] compressWithFixedQuality(BufferedImage image, float quality) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
            if (!writers.hasNext())
                throw new IllegalStateException("No JPEG writer found");

            ImageWriter writer = writers.next();
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(outputStream)) {
                writer.setOutput(ios);
                ImageWriteParam param = writer.getDefaultWriteParam();
                if (param.canWriteCompressed()) {
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(quality);
                }
                writer.write(null, new IIOImage(image, null, null), param);
            } finally {
                writer.dispose();
            }
            return outputStream.toByteArray();
        }
    }

    /**
     * Optimized PDF compression: Deep compression of internal images
     */
    public byte[] compressPdf(byte[] originalPdfBytes) throws IOException {
        // NOTE: Input size validation should happen before this method if strict limits
        // are needed.
        long thresholdBytes = 100 * 1024;
        if (originalPdfBytes.length < thresholdBytes) {
            return originalPdfBytes; // Already small
        }

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(originalPdfBytes);
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            PdfReader reader = new PdfReader(inputStream);
            WriterProperties writerProps = new WriterProperties()
                    .setCompressionLevel(9)
                    .setFullCompressionMode(true);

            PdfWriter writer = new PdfWriter(outputStream, writerProps);
            PdfDocument pdfDoc = new PdfDocument(reader, writer);

            long targetImageSize = 300 * 1024;
            optimizePdfImages(pdfDoc, targetImageSize);

            pdfDoc.close();

            byte[] compressedBytes = outputStream.toByteArray();
            log.info("PDF compressed from {}KB to {}KB", originalPdfBytes.length / 1024, compressedBytes.length / 1024);

            if (compressedBytes.length > originalPdfBytes.length) {
                return originalPdfBytes;
            }
            return compressedBytes;
        } catch (Exception e) {
            log.error("Error during PDF compression", e);
            throw new IOException("PDF Compression failed", e);
        }
    }

    private void optimizePdfImages(PdfDocument pdfDoc, long targetImageSize) {
        int numberOfPages = pdfDoc.getNumberOfPages();
        for (int i = 1; i <= numberOfPages; i++) {
            PdfPage page = pdfDoc.getPage(i);
            PdfDictionary resources = page.getResources().getResource(PdfName.XObject);
            if (resources != null) {
                optimizePdfDictionary(resources, targetImageSize);
            }
        }
    }

    private void optimizePdfDictionary(PdfDictionary resources, long targetImageSize) {
        Set<PdfName> keys = new HashSet<>(resources.keySet());
        for (PdfName key : keys) {
            PdfObject obj = resources.get(key);
            if (obj.isIndirectReference()) {
                obj = ((com.itextpdf.kernel.pdf.PdfIndirectReference) obj).getRefersTo();
            }

            if (obj != null && obj.isStream()) {
                PdfStream stream = (PdfStream) obj;
                PdfName subtype = stream.getAsName(PdfName.Subtype);

                if (PdfName.Image.equals(subtype)) {
                    optimizeSinglePdfImage(stream, resources, key, targetImageSize);
                } else if (PdfName.Form.equals(subtype)) {
                    PdfDictionary formResources = stream.getAsDictionary(PdfName.Resources);
                    if (formResources != null) {
                        PdfDictionary formXObjects = formResources.getAsDictionary(PdfName.XObject);
                        if (formXObjects != null) {
                            optimizePdfDictionary(formXObjects, targetImageSize);
                        }
                    }
                }
            }
        }
    }

    private void optimizeSinglePdfImage(PdfStream stream, PdfDictionary resources, PdfName key, long targetSize) {
        try {
            PdfImageXObject imageXObject = new PdfImageXObject(stream);
            byte[] imageBytes = imageXObject.getImageBytes();

            if (imageBytes == null || imageBytes.length < targetSize)
                return;

            BufferedImage bi = imageXObject.getBufferedImage();
            if (bi == null)
                return;

            if (bi.getWidth() > 1800 || bi.getHeight() > 1800) {
                bi = Thumbnails.of(bi).size(1800, 1800).asBufferedImage();
            }

            byte[] compressedBytes = compressWithFixedQuality(bi, 0.75f);

            if (compressedBytes.length < imageBytes.length) {
                ImageData newData = ImageDataFactory.create(compressedBytes);
                PdfImageXObject newXObject = new PdfImageXObject(newData);
                resources.put(key, newXObject.getPdfObject());
                log.debug("Optimized PDF embedding: {}KB -> {}KB", imageBytes.length / 1024,
                        compressedBytes.length / 1024);
            }
        } catch (Exception e) {
            log.warn("Failed to optimize PDF image: {}", e.getMessage());
        }
    }

    /**
     * Check if processing is needed
     */
    public boolean needsProcessing(long fileSizeBytes, String contentType) {
        long targetSizeBytes = documentProperties.getFileSize().getTargetFileSizeKb() * 1024;
        return fileSizeBytes > targetSizeBytes;
    }
}
