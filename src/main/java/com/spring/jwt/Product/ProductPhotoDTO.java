package com.spring.jwt.Product;

import lombok.Data;

import java.time.LocalDateTime;
@Data
public class ProductPhotoDTO{

    private String imageUrl;
    private LocalDateTime uploadedAt;
    private String message;
}

