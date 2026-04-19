package com.spring.jwt.ProductPhoto;

import com.spring.jwt.EmployeeFarmerSurvey.BaseResponseDTO1;
import com.spring.jwt.Enums.ImageType;
import com.spring.jwt.Enums.PhotoType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * REST Controller responsible for handling Product Photo operations.
 *
 * This controller supports:
 * - Uploading product photo images
 * - Fetching photo details by image ID
 * - Fetching photo details by product ID
 * - Updating (patching) the product image only
 *
 * All responses are wrapped using BaseResponseDTO1
 * for consistency across the application.
 */
@RestController
@RequestMapping("/api/v1/product-photo")
@RequiredArgsConstructor
public class ProductPhotoController {
    /**
     * Service layer dependency handling all business logic
     * related to product photo operations.
     */

    private final ProductPhotoService productPhotoService;

    /**
     * Upload a product photo for a given product
     *
     * Business Rules:
     * - A valid productId must be provided
     * - Only image files are allowed
     * - Only one photo is allowed per product
     *
     * @param productId ID of the product
     * @param photoType Type of photo
     * @param photo Multipart image file to be uploaded
     * @return Created ProductPhotoResponseUploadDTO
     */
    @PostMapping("/upload")
    public ResponseEntity<BaseResponseDTO1<ProductPhotoResponseUploadDTO>> uploadProductPhoto(
            @RequestParam Long productId,
            @RequestParam ImageType photoType,
            @RequestParam("photo") MultipartFile photo
    ) {
        ProductPhotoResponseUploadDTO response =
                productPhotoService.uploadProductPhoto(productId, photoType, photo);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new BaseResponseDTO1<>(
                        "201",
                        "Product photo uploaded successfully",
                        response
                ));
    }

    /**
     * Fetch product photo details using image ID.
     *
     * @param imageId Unique ID of the product image
     * @return ProductPhotoResponseDTO
     */
    @GetMapping("/{imageId}")
    public ResponseEntity<BaseResponseDTO1<ProductPhotoResponseDTO>> getByImageId(
            @PathVariable Long imageId
    ) {
        ProductPhotoResponseDTO response =
                productPhotoService.getPhotoById(imageId);
        return ResponseEntity.ok(
                new BaseResponseDTO1<>(
                        "200",
                        "Product photo fetched successfully",
                        response
                )
        );

    }

    /**
     * Fetch product photo details using product ID.
     *
     * Use case:
     * - Mobile / frontend wants product image while viewing product details
     *
     * @param productId ID of the product
     * @return ProductPhotoResponseDTO
     */
    @GetMapping("/product/{productId}")
    public ResponseEntity<BaseResponseDTO1<ProductPhotoResponseDTO>> getByProductId(
            @PathVariable Long productId
    ) {
        ProductPhotoResponseDTO response =
                productPhotoService.getPhotoByProductId(productId);
        return ResponseEntity.ok(
                new BaseResponseDTO1<>(
                        "200",
                        "Product photo fetched successfully by product ID",
                        response
                )
        );

    }

    /**
     * Update (replace) the product photo image.
     *
     * Notes:
     * - This is a PATCH operation because only the image is updated
     * - Existing product photo metadata remains unchanged
     *
     * @param imageId ID of the product image to be updated
     * @param image New image file
     * @return Updated ProductPhotoResponseDTO
     */
    /**
     * Get all photos for a product (returns both COVERIMAGE and PHOTO types).
     */
    @GetMapping("/product/{productId}/all")
    public ResponseEntity<BaseResponseDTO1<List<ProductPhotoResponseDTO>>> getAllByProductId(
            @PathVariable Long productId) {
        List<ProductPhotoResponseDTO> response = productPhotoService.getAllPhotosByProductId(productId);
        return ResponseEntity.ok(new BaseResponseDTO1<>("200", "All photos fetched", response));
    }

    /**
     * Batch fetch all photos for many products (single query) — use for catalog grids instead of N × /all calls.
     */
    @PostMapping("/batch/all-by-product-ids")
    public ResponseEntity<BaseResponseDTO1<List<ProductPhotosBatchRow>>> getAllPhotosBatch(
            @RequestBody List<Long> productIds) {
        List<Long> safe = productIds == null ? List.of() : productIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .limit(200)
                .collect(Collectors.toList());
        List<ProductPhotosBatchRow> response = productPhotoService.getAllPhotosByProductIds(safe);
        return ResponseEntity.ok(new BaseResponseDTO1<>("200", "Batch photos fetched", response));
    }

    /**
     * Get a specific photo type for a product.
     * photoType = COVERIMAGE or PHOTO
     */
    @GetMapping("/product/{productId}/type/{photoType}")
    public ResponseEntity<BaseResponseDTO1<ProductPhotoResponseDTO>> getByProductIdAndType(
            @PathVariable Long productId,
            @PathVariable ImageType photoType) {
        ProductPhotoResponseDTO response = productPhotoService.getPhotoByProductIdAndType(productId, photoType);
        return ResponseEntity.ok(new BaseResponseDTO1<>("200", "Photo fetched", response));
    }

    /**
     * Update (replace) product image by productId + photoType.
     * Works whether or not a photo already exists — type-safe upsert.
     * photoType = COVERIMAGE or PHOTO
     */
    @PatchMapping("/product/{productId}")
    public ResponseEntity<BaseResponseDTO1<ProductPhotoResponseDTO>> updateProductImageByProductId(
            @PathVariable Long productId,
            @RequestParam("image") MultipartFile image,
            @RequestParam ImageType photoType) {
        ProductPhotoResponseDTO response =
                productPhotoService.updateProductImageByProductId(productId, image, photoType);
        return ResponseEntity.ok(new BaseResponseDTO1<>("200", "Product photo updated successfully", response));
    }

    /**
     * Update product photo by imageId (use only if you know the imageId).
     */
    @PatchMapping("/{imageId}")
    public ResponseEntity<BaseResponseDTO1<ProductPhotoResponseDTO>> updateProductImage(
            @PathVariable Long imageId,
            @RequestParam MultipartFile image) {
        ProductPhotoResponseDTO response = productPhotoService.updateProductImage(imageId, image);
        return ResponseEntity.ok(new BaseResponseDTO1<>("200", "Product photo image updated successfully", response));
    }

    // ── Delete endpoints ──────────────────────────────────────────────────────

    /**
     * Delete a specific photo by productId + imageId.
     * Validates ownership — imageId must belong to the given productId.
     *
     * DELETE /api/v1/product-photo/product/{productId}/image/{imageId}
     */
    @DeleteMapping("/product/{productId}/image/{imageId}")
    public ResponseEntity<BaseResponseDTO1<Void>> deletePhotoByImageId(
            @PathVariable Long productId,
            @PathVariable Long imageId) {
        productPhotoService.deletePhotoByImageId(productId, imageId);
        return ResponseEntity.ok(new BaseResponseDTO1<>(
                "200", "Photo deleted successfully", null));
    }

    /**
     * Delete all photos for a product.
     *
     * DELETE /api/v1/product-photo/product/{productId}/all
     */
    @DeleteMapping("/product/{productId}/all")
    public ResponseEntity<BaseResponseDTO1<Void>> deleteAllPhotosForProduct(
            @PathVariable Long productId) {
        productPhotoService.deleteAllPhotosForProduct(productId);
        return ResponseEntity.ok(new BaseResponseDTO1<>(
                "200", "All photos deleted for product " + productId, null));
    }

}
