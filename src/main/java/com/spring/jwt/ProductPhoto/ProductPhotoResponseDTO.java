package com.spring.jwt.ProductPhoto;

import com.spring.jwt.Enums.ImageType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProductPhotoResponseDTO {
    private Long imageId;
    private Long productId;
    // Base64-encoded image data for frontend rendering
    private String imageData;
    private String contentType;
    private String fileName;
    private ImageType imageType;
    private LocalDateTime uploadedAt;
}
