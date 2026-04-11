package com.spring.jwt.ProductPhoto;

import com.spring.jwt.Enums.ImageType;
import com.spring.jwt.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductPhotoRepository extends JpaRepository<ProductImage, Long> {

    boolean existsByProduct_ProductId(Long productId);

    Optional<ProductImage> findByProduct_ProductId(Long productId);

    Optional<ProductImage> findByProduct_ProductIdAndImageType(Long productId, ImageType imageType);

    boolean existsByProduct_ProductIdAndImageType(Long productId, ImageType imageType);

    List<ProductImage> findAllByProduct_ProductId(Long productId);

    /**
     * Batch fetch — one query for all product IDs.
     * Used by getAll / paginated listing to eliminate N+1 on photos.
     * Returns only the first/cover photo per product (COVERIMAGE preferred).
     */
    @Query("SELECT pi FROM ProductImage pi WHERE pi.product.productId IN :productIds")
    List<ProductImage> findAllByProductIdIn(@Param("productIds") Collection<Long> productIds);
}
