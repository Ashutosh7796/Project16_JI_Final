package com.spring.jwt.ProductBuyConfirmed;

import com.spring.jwt.entity.ProductBuyConfirmed;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ProductBuyConfirmedRepository extends JpaRepository<ProductBuyConfirmed, Long> {

    @Query("SELECT COUNT(p) FROM ProductBuyConfirmed p WHERE p.createdAt >= :start AND p.createdAt < :end")
    long countCreatedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    Optional<ProductBuyConfirmed> findFirstByPaymentIdOrderByIdDesc(String paymentId);
}