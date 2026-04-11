package com.spring.jwt.ProductPhoto;

import com.spring.jwt.Enums.ImageType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProductPhotoResponseUploadDTO {
    private Long imageId;
    private Long productId;
    private ImageType imageType;
    private LocalDateTime uploadedAt;
}
