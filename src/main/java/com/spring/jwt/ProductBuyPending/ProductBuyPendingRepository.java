package com.spring.jwt.ProductBuyPending;

import com.spring.jwt.entity.ProductBuyPending;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductBuyPendingRepository extends JpaRepository<ProductBuyPending, Long> {
}