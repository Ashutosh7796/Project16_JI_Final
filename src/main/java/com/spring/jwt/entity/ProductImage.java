package com.spring.jwt.entity;

import com.spring.jwt.Enums.ImageType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString(exclude = "product")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@Entity
@Table(name = "product_images",
        indexes = {
                @Index(name = "idx_product_images_product_id", columnList = "product_id"),
                @Index(name = "idx_product_images_type", columnList = "image_type")
        }
)
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long imageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "image_data", columnDefinition = "LONGBLOB")
    private byte[] imageData;

    @Column(name = "content_type", length = 50)
    private String contentType;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "image_type", length = 20)
    private ImageType imageType;

    private LocalDateTime uploadedAt = LocalDateTime.now();
}
