package com.spring.jwt.Product;

import com.spring.jwt.entity.Product;
import org.apache.coyote.BadRequestException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ProductService {

    ProductDTO create(ProductDTO dto) throws BadRequestException;

    ProductDTO getById(Long id) throws BadRequestException;

    List<ProductDTO> getAll();

    ProductDTO patch(Long id, ProductDTO dto) throws BadRequestException;

    void delete(Long id) throws BadRequestException;

    Page<ProductDTO> getAllByProductType(Product.ProductType productType ,Pageable pageable);

    Page<ProductDTO> getAllByProductTypeAndCategory(Product.ProductType productType, Product.Category category, Pageable pageable);
}
