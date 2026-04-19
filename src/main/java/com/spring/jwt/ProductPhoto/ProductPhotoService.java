package com.spring.jwt.ProductPhoto;


import com.spring.jwt.Enums.ImageType;
import com.spring.jwt.Enums.PhotoType;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ProductPhotoService {


    ProductPhotoResponseUploadDTO uploadProductPhoto(Long productId, ImageType photoType, MultipartFile file);

    ProductPhotoResponseDTO getPhotoById(Long imageId);

    ProductPhotoResponseDTO getPhotoByProductId(Long productId);

    List<ProductPhotoResponseDTO> getAllPhotosByProductId(Long productId);

    /**
     * All photos for many products in one DB round-trip (for catalog / list UIs).
     */
    List<ProductPhotosBatchRow> getAllPhotosByProductIds(List<Long> productIds);

    ProductPhotoResponseDTO getPhotoByProductIdAndType(Long productId, ImageType imageType);

    ProductPhotoResponseDTO updateProductImage(Long imageId, MultipartFile file);

    ProductPhotoResponseDTO updateProductImageByProductId(Long productId, MultipartFile file, ImageType photoType);

    // ── Delete operations ─────────────────────────────────────────────────────

    /**
     * Delete a specific photo by its imageId.
     * Validates the photo belongs to the given productId before deleting.
     */
    void deletePhotoByImageId(Long productId, Long imageId);

    /**
     * Delete all photos for a product.
     */
    void deleteAllPhotosForProduct(Long productId);
}
