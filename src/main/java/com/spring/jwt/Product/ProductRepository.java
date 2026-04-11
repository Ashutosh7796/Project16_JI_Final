package com.spring.jwt.Product;

import com.spring.jwt.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    // Fetch all products with sections and section contents in 2 queries (batch fetch)
    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.sections s LEFT JOIN FETCH s.content")
    List<Product> findAllWithSections();

    // Single product with full graph — used by getById
    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.sections s LEFT JOIN FETCH s.content WHERE p.productId = :id")
    Optional<Product> findByIdWithSections(@Param("id") Long id);

    // Paginated by type — count query avoids the join for counting
    @Query(value = "SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.sections s LEFT JOIN FETCH s.content WHERE p.productType = :type",
           countQuery = "SELECT COUNT(p) FROM Product p WHERE p.productType = :type")
    Page<Product> findByProductTypeWithSections(@Param("type") Product.ProductType type, Pageable pageable);

    // Paginated by type + category
    @Query(value = "SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.sections s LEFT JOIN FETCH s.content WHERE p.productType = :type AND p.category = :category",
           countQuery = "SELECT COUNT(p) FROM Product p WHERE p.productType = :type AND p.category = :category")
    Page<Product> findByProductTypeAndCategoryWithSections(@Param("type") Product.ProductType type, @Param("category") Product.Category category, Pageable pageable);

    // Keep originals for any other usages
    Page<Product> findByProductType(Product.ProductType productType, Pageable pageable);
    Page<Product> findByProductTypeAndCategory(Product.ProductType productType, Product.Category category, Pageable pageable);
}
