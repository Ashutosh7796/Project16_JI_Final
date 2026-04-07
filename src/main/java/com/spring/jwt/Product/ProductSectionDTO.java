package com.spring.jwt.Product;

import com.spring.jwt.Enums.ProductSectionType;
import lombok.Data;

import java.util.List;

@Data
public class ProductSectionDTO {
    private ProductSectionType sectionType;
    private List<String> content;
}
