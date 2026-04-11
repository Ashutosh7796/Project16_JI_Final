package com.spring.jwt.Product;

import com.spring.jwt.ProductPhoto.ProductPhotoRepository;
import com.spring.jwt.entity.Product;
import com.spring.jwt.entity.ProductSection;
import com.spring.jwt.exception.ResourceNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.BadRequestException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductPhotoRepository productPhotoRepository;

    @Override
    public ProductDTO create(ProductDTO dto) throws BadRequestException {

        if (dto == null) {
            throw new BadRequestException("Product payload cannot be null");
        }

        if (dto.getProductName() == null || dto.getProductName().isBlank()) {
            throw new BadRequestException("Product name is required");
        }

        if (dto.getProductType() == null) {
            throw new BadRequestException("Product type is required");
        }

        Product product = mapToEntity(dto);
        Product saved = productRepository.save(product);

        return mapToDto(saved);
    }


    @Override
    @Transactional
    public ProductDTO getById(Long productId) throws BadRequestException {
        Product product = productRepository.findByIdWithSections(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id " + productId));
        return mapToDto(product);
    }

    @Override
    @Transactional
    public List<ProductDTO> getAll() {
        List<Product> products = productRepository.findAllWithSections();
        return mapToDtoList(products);
    }

    @Override
    @Transactional
    public Page<ProductDTO> getAllByProductType(Product.ProductType productType, Pageable pageable) {
        Page<Product> page = productRepository.findByProductTypeWithSections(productType, pageable);
        if (pageable.getPageNumber() >= page.getTotalPages() && page.getTotalPages() > 0) {
            throw new ResourceNotFoundException("Page not found. Requested Page: " + pageable.getPageNumber());
        }
        return mapPageToDto(page);
    }

    @Override
    @Transactional
    public Page<ProductDTO> getAllByProductTypeAndCategory(Product.ProductType productType, Product.Category category, Pageable pageable) {
        Page<Product> page;
        if (category == Product.Category.ALL) {
            page = productRepository.findByProductTypeWithSections(productType, pageable);
        } else {
            page = productRepository.findByProductTypeAndCategoryWithSections(productType, category, pageable);
        }
        if (pageable.getPageNumber() >= page.getTotalPages() && page.getTotalPages() > 0) {
            throw new ResourceNotFoundException("Page not found. Requested Page: " + pageable.getPageNumber());
        }
        return mapPageToDto(page);
    }

    @Override
    public ProductDTO patch(Long id, ProductDTO dto) throws BadRequestException {

        if (dto == null) {
            throw new BadRequestException("Patch payload cannot be null");
        }

        Product product = getProduct(id);

        if (dto.getProductName() != null) {
            product.setProductName(dto.getProductName());
        }

        if (dto.getProductType() != null) {
            product.setProductType(dto.getProductType());
        }

        if (dto.getPrice() != null) {
            product.setPrice(dto.getPrice());
        }

        if (dto.getOffers() != null) {
            product.setOffers(dto.getOffers());
        }

        if (dto.getActive() != null) {
            product.setActive(dto.getActive());
        }

        if (dto.getSections() != null) {
            product.getSections().clear();
            product.getSections().addAll(mapSections(dto.getSections(), product));
        }

        return mapToDto(product);
    }


    @Override
    public void delete(Long id) throws BadRequestException {
        Product product = getProduct(id);
        productRepository.delete(product);
    }


    private Product getProduct(Long id) throws BadRequestException {

        if (id == null) {
            throw new BadRequestException("Product id cannot be null");
        }

        return productRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Product not found with id: " + id));
    }

    private Product mapToEntity(ProductDTO dto) throws BadRequestException {

        Product product = new Product();
        product.setProductName(dto.getProductName());
        product.setProductType(dto.getProductType());
        product.setPrice(roundToTwoDecimals(dto.getPrice()));
        product.setOffers(roundToTwoDecimals(dto.getOffers()));
        product.setActive(dto.getActive() != null ? dto.getActive() : true);

        if (dto.getSections() != null) {
            product.setSections(mapSections(dto.getSections(), product));
        }

        return product;
    }

    private static double roundToTwoDecimals(Double value) {
        if (value == null) return 0.0;
        return Math.round(value * 100.0) / 100.0;
    }

    private Set<ProductSection> mapSections(List<ProductSectionDTO> dtos, Product product) throws BadRequestException {
        if (dtos.isEmpty()) {
            throw new BadRequestException("Product sections cannot be empty");
        }

        return dtos.stream().map(dto -> {

            if (dto.getSectionType() == null) {
                try {
                    throw new BadRequestException("Section type is required");
                } catch (BadRequestException e) {
                    throw new RuntimeException(e);
                }
            }

            ProductSection section = new ProductSection();
            section.setSectionType(dto.getSectionType());
            section.setContent(dto.getContent() != null ? new java.util.LinkedHashSet<>(dto.getContent()) : new java.util.LinkedHashSet<>());
            section.setProduct(product);
            return section;

        }).collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    // ── Batch helpers (eliminates N+1 on photo fetch) ────────────────────────

    /**
     * Maps a list of products to DTOs with a single batch photo query.
     * Total queries: 1 (products+sections) + 1 (all photos for all IDs) = 2 flat.
     */
    private List<ProductDTO> mapToDtoList(List<Product> products) {
        if (products.isEmpty()) return java.util.Collections.emptyList();

        java.util.Set<Long> ids = products.stream()
                .map(Product::getProductId)
                .collect(java.util.stream.Collectors.toSet());

        java.util.Map<Long, List<com.spring.jwt.entity.ProductImage>> photoMap =
                productPhotoRepository.findAllByProductIdIn(ids).stream()
                        .collect(java.util.stream.Collectors.groupingBy(
                                pi -> pi.getProduct().getProductId()));

        return products.stream()
                .map(p -> mapToDto(p, photoMap.getOrDefault(p.getProductId(), java.util.Collections.emptyList())))
                .toList();
    }

    /**
     * Maps a Page of products to DTOs with a single batch photo query.
     */
    private Page<ProductDTO> mapPageToDto(Page<Product> page) {
        if (page.isEmpty()) return page.map(p -> mapToDto(p, java.util.Collections.emptyList()));

        java.util.Set<Long> ids = page.getContent().stream()
                .map(Product::getProductId)
                .collect(java.util.stream.Collectors.toSet());

        java.util.Map<Long, List<com.spring.jwt.entity.ProductImage>> photoMap =
                productPhotoRepository.findAllByProductIdIn(ids).stream()
                        .collect(java.util.stream.Collectors.groupingBy(
                                pi -> pi.getProduct().getProductId()));

        return page.map(p -> mapToDto(p, photoMap.getOrDefault(p.getProductId(), java.util.Collections.emptyList())));
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    /** Single-product mapping — fetches all photos for this product (used by getById). */
    private ProductDTO mapToDto(Product product) {
        List<com.spring.jwt.entity.ProductImage> photos =
                productPhotoRepository.findAllByProduct_ProductId(product.getProductId());
        return mapToDto(product, photos);
    }

    /** Core mapping — accepts pre-fetched photos list, zero DB calls. */
    private ProductDTO mapToDto(Product product, List<com.spring.jwt.entity.ProductImage> photos) {
        ProductDTO dto = new ProductDTO();
        dto.setProductId(product.getProductId());
        dto.setProductName(product.getProductName());
        dto.setProductType(product.getProductType());
        dto.setCategory(product.getCategory());
        dto.setPrice(roundToTwoDecimals(product.getPrice()));
        dto.setOffers(roundToTwoDecimals(product.getOffers()));
        dto.setActive(product.getActive());

        if (product.getSections() != null) {
            dto.setSections(product.getSections().stream().map(s -> {
                ProductSectionDTO sd = new ProductSectionDTO();
                sd.setSectionType(s.getSectionType());
                sd.setContent(s.getContent() != null
                        ? new java.util.ArrayList<>(s.getContent())
                        : new java.util.ArrayList<>());
                return sd;
            }).toList());
        }

        // Cover photo — prefer COVERIMAGE, fall back to first available
        com.spring.jwt.entity.ProductImage cover = photos.stream()
                .filter(p -> p.getImageType() == com.spring.jwt.Enums.ImageType.COVERIMAGE)
                .findFirst()
                .orElse(photos.isEmpty() ? null : photos.get(0));

        ProductPhotoDTO coverDTO = new ProductPhotoDTO();
        if (cover != null && cover.getImageData() != null) {
            coverDTO.setImageUrl(java.util.Base64.getEncoder().encodeToString(cover.getImageData()));
            coverDTO.setUploadedAt(cover.getUploadedAt());
            coverDTO.setMessage("Product Photo Found");
        } else {
            coverDTO.setMessage("Product Photo Not Uploaded");
        }
        dto.setPhotoDTO(coverDTO);

        // All photos for gallery view
        dto.setPhotos(photos.stream().map(p -> {
            ProductPhotoDTO pd = new ProductPhotoDTO();
            if (p.getImageData() != null) {
                pd.setImageUrl(java.util.Base64.getEncoder().encodeToString(p.getImageData()));
            }
            pd.setUploadedAt(p.getUploadedAt());
            pd.setMessage(p.getImageType() != null ? p.getImageType().name() : "PHOTO");
            return pd;
        }).toList());

        return dto;
    }
}
