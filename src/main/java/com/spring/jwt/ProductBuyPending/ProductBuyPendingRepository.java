package com.spring.jwt.ProductBuyPending;

import com.spring.jwt.entity.ProductBuyPending;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface ProductBuyPendingRepository extends JpaRepository<ProductBuyPending, Long> {

    @Query("SELECT COUNT(p) FROM ProductBuyPending p WHERE p.createdAt >= :start AND p.createdAt < :end")
    long countCreatedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * One round-trip for the dashboard chart: pending + confirmed rows in [start, end).
     */
    @Query(
            value = """
                    SELECT IFNULL((SELECT COUNT(*) FROM product_buy_pending p
                    WHERE p.created_at >= :start AND p.created_at < :end), 0)
                    + IFNULL((SELECT COUNT(*) FROM product_buy_confirmed c
                    WHERE c.created_at >= :start AND c.created_at < :end), 0)
                    """,
            nativeQuery = true
    )
    long countAllOrdersBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}