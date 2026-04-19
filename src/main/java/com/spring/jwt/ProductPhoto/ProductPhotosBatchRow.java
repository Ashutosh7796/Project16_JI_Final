package com.spring.jwt.ProductPhoto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * One product's photo list in a batch response.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductPhotosBatchRow {
    private Long productId;
    private List<ProductPhotoResponseDTO> photos;
}
