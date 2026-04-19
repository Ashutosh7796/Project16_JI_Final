package com.spring.jwt.ProductPhoto;

import com.spring.jwt.Enums.ImageType;
import com.spring.jwt.Product.ProductRepository;
import com.spring.jwt.entity.Product;
import com.spring.jwt.entity.ProductImage;
import com.spring.jwt.exception.ResourceNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductPhotoServiceImpl implements ProductPhotoService {

    private final ProductPhotoRepository productPhotoRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional
    public ProductPhotoResponseUploadDTO uploadProductPhoto(
            Long productId, ImageType photoType, MultipartFile file) {
        validateProductId(productId);
        validateImage(file);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product not found with ID: " + productId));
        try {
            long start = System.currentTimeMillis();

            ProductImage image = productPhotoRepository
                    .findByProduct_ProductIdAndImageType(productId, photoType)
                    .orElseGet(ProductImage::new);

            applyFileToImage(image, product, photoType, file);
            ProductImage saved = productPhotoRepository.save(image);

            log.info("Photo upserted: productId={}, type={}, size={}KB, in {}ms",
                    productId, photoType, file.getSize() / 1024,
                    System.currentTimeMillis() - start);
            return mapToResponseUpload(saved);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to upload photo for product {}: {}", productId, e.getMessage(), e);
            throw new RuntimeException("Failed to upload product photo: " + e.getMessage(), e);
        }
    }

    @Override
    public ProductPhotoResponseDTO getPhotoById(Long imageId) {
        ProductImage image = productPhotoRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product photo not found with ID: " + imageId));
        return mapToResponse(image);
    }

    @Override
    public ProductPhotoResponseDTO getPhotoByProductId(Long productId) {
        ProductImage image = productPhotoRepository
                .findByProduct_ProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No photo found for product ID: " + productId));
        return mapToResponse(image);
    }

    @Override
    public List<ProductPhotoResponseDTO> getAllPhotosByProductId(Long productId) {
        return productPhotoRepository.findAllByProduct_ProductId(productId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public List<ProductPhotosBatchRow> getAllPhotosByProductIds(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        List<Long> distinct = productIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .limit(200)
                .collect(Collectors.toList());
        if (distinct.isEmpty()) {
            return List.of();
        }
        List<ProductImage> all = productPhotoRepository.findAllByProductIdIn(distinct);
        Map<Long, List<ProductImage>> grouped = new LinkedHashMap<>();
        for (Long id : distinct) {
            grouped.put(id, new ArrayList<>());
        }
        for (ProductImage pi : all) {
            Long pid = pi.getProduct().getProductId();
            grouped.computeIfAbsent(pid, k -> new ArrayList<>()).add(pi);
        }
        List<ProductPhotosBatchRow> out = new ArrayList<>();
        for (Long id : distinct) {
            List<ProductPhotoResponseDTO> photos = grouped.getOrDefault(id, List.of()).stream()
                    .map(this::mapToResponse)
                    .toList();
            out.add(new ProductPhotosBatchRow(id, photos));
        }
        return out;
    }

    @Override
    public ProductPhotoResponseDTO getPhotoByProductIdAndType(Long productId, ImageType imageType) {
        ProductImage image = productPhotoRepository
                .findByProduct_ProductIdAndImageType(productId, imageType)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No " + imageType + " photo found for product ID: " + productId));
        return mapToResponse(image);
    }

    @Override
    @Transactional
    public ProductPhotoResponseDTO updateProductImage(Long imageId, MultipartFile file) {
        validateImage(file);
        ProductImage image = productPhotoRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product photo not found with ID: " + imageId));
        try {
            applyFileToImage(image, image.getProduct(), image.getImageType(), file);
            return mapToResponse(productPhotoRepository.save(image));
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to update photo {}: {}", imageId, e.getMessage(), e);
            throw new RuntimeException("Failed to update product photo: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public ProductPhotoResponseDTO updateProductImageByProductId(
            Long productId, MultipartFile file, ImageType photoType) {
        validateProductId(productId);
        validateImage(file);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product not found with ID: " + productId));
        try {
            ProductImage image = productPhotoRepository
                    .findByProduct_ProductIdAndImageType(productId, photoType)
                    .orElseGet(ProductImage::new);

            applyFileToImage(image, product, photoType, file);
            return mapToResponse(productPhotoRepository.save(image));
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to update photo for productId={}, type={}: {}",
                    productId, photoType, e.getMessage(), e);
            throw new RuntimeException("Failed to update product photo: " + e.getMessage(), e);
        }
    }

    // ── Delete operations ─────────────────────────────────────────────────────

    @Override
    @Transactional
    public void deletePhotoByImageId(Long productId, Long imageId) {
        validateProductId(productId);
        if (imageId == null) throw new IllegalArgumentException("Image ID must not be null");

        ProductImage image = productPhotoRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Photo not found with ID: " + imageId));

        // Ownership check — ensure this photo actually belongs to the given product
        if (!image.getProduct().getProductId().equals(productId)) {
            throw new IllegalArgumentException(
                    "Photo " + imageId + " does not belong to product " + productId);
        }

        productPhotoRepository.delete(image);
        log.info("Deleted photo: imageId={}, productId={}, type={}", imageId, productId, image.getImageType());
    }

    @Override
    @Transactional
    public void deleteAllPhotosForProduct(Long productId) {
        validateProductId(productId);

        // Verify product exists first
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product not found with ID: " + productId);
        }

        List<ProductImage> photos = productPhotoRepository.findAllByProduct_ProductId(productId);
        if (photos.isEmpty()) {
            throw new ResourceNotFoundException("No photos found for product ID: " + productId);
        }

        productPhotoRepository.deleteAll(photos);
        log.info("Deleted all {} photos for productId={}", photos.size(), productId);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void applyFileToImage(ProductImage image, Product product,
                                   ImageType photoType, MultipartFile file) {
        try {
            image.setProduct(product);
            image.setImageType(photoType);
            image.setImageData(file.getBytes());          // raw bytes — no Base64 bloat
            image.setContentType(file.getContentType());
            image.setFileName(file.getOriginalFilename());
            image.setUploadedAt(LocalDateTime.now());
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to read image file: " + e.getMessage(), e);
        }
    }

    private void validateProductId(Long productId) {
        if (productId == null) {
            throw new IllegalArgumentException("Product ID must not be null");
        }
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Image file is required and cannot be empty");
        }
        if (file.getContentType() == null || !file.getContentType().startsWith("image/")) {
            throw new IllegalArgumentException(
                    "Invalid file type '" + file.getContentType() + "'. Only image files are allowed (JPEG, PNG, WEBP, etc.)");
        }
        long maxSize = 10L * 1024 * 1024; // 10MB per image
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException(
                    "Image size " + (file.getSize() / 1024 / 1024) + "MB exceeds the 10MB limit");
        }
    }

    // ── Mappers ──────────────────────────────────────────────────────────────

    private ProductPhotoResponseDTO mapToResponse(ProductImage entity) {
        ProductPhotoResponseDTO dto = new ProductPhotoResponseDTO();
        dto.setImageId(entity.getImageId());
        dto.setProductId(entity.getProduct().getProductId());
        // Encode to Base64 only on response — DB stores raw bytes
        if (entity.getImageData() != null) {
            dto.setImageData(Base64.getEncoder().encodeToString(entity.getImageData()));
        }
        dto.setContentType(entity.getContentType());
        dto.setFileName(entity.getFileName());
        dto.setImageType(entity.getImageType());
        dto.setUploadedAt(entity.getUploadedAt());
        return dto;
    }

    private ProductPhotoResponseUploadDTO mapToResponseUpload(ProductImage entity) {
        ProductPhotoResponseUploadDTO dto = new ProductPhotoResponseUploadDTO();
        dto.setImageId(entity.getImageId());
        dto.setProductId(entity.getProduct().getProductId());
        dto.setImageType(entity.getImageType());
        dto.setUploadedAt(entity.getUploadedAt());
        return dto;
    }
}
