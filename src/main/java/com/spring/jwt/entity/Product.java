package com.spring.jwt.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@ToString(exclude = "sections")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(
        name = "products",
        indexes = {
                @Index(name = "idx_products_name", columnList = "productName"),
                @Index(name = "idx_products_type", columnList = "productType"),
                @Index(name = "idx_products_active", columnList = "active")
        }
)
@AllArgsConstructor
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long productId;

    private String productName;

    @Enumerated(EnumType.STRING)
    private ProductType productType;

    @Enumerated(EnumType.STRING)
    private Category category;

    private Double price;
    private Double offers;
    private Boolean active = true;

    /**
     * Sellable units on hand. {@code null} means unlimited inventory (no reservation / stock checks).
     */
    @Column(name = "stock_on_hand")
    private Integer stockOnHand;

    @Column(name = "stock_reserved", nullable = false)
    private Integer stockReserved = 0;

    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(
            mappedBy = "product",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private Set<ProductSection> sections = new LinkedHashSet<>();

    @OneToMany(
            mappedBy = "product",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private Set<ProductImage> images = new LinkedHashSet<>();

    public enum ProductType {
        SEED, FERTILIZER
    }
    public enum Category {
        RABI, KHARIP, SUMMER, ALL
    }
}
