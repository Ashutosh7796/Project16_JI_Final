package com.spring.jwt.entity;

import com.spring.jwt.Enums.ProductSectionType;
import jakarta.persistence.*;
import lombok.*;

import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@ToString(exclude = "product")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "product_sections")
@NoArgsConstructor
@AllArgsConstructor
public class ProductSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Enumerated(EnumType.STRING)
    private ProductSectionType sectionType;

    @ElementCollection
    @CollectionTable(
            name = "product_section_contents",
            joinColumns = @JoinColumn(name = "section_id")
    )
    @Column(columnDefinition = "TEXT")
    private Set<String> content = new LinkedHashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;
}
