package com.spring.jwt.Product;

import com.spring.jwt.entity.Product;
import lombok.Data;

import java.util.List;

@Data
public class ProductDTO {
    private Long productId;
    private String productName;
    private Product.ProductType productType;
    private Product.Category category;
    private Double price;
    private Double offers;
    private Boolean active;
    private List<ProductSectionDTO> sections;
    // Cover photo (COVERIMAGE type, or first available) — for listing cards
    private ProductPhotoDTO photoDTO;
    // All photos for this product — for product detail gallery
    private List<ProductPhotoDTO> photos;
}
