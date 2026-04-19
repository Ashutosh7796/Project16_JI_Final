package com.spring.jwt.checkout;

import com.spring.jwt.entity.Product;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atomic inventory updates for checkout (separate from {@link com.spring.jwt.Product.ProductRepository}).
 */
public interface ProductInventoryRepository extends Repository<Product, Long> {

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE products
            SET stock_reserved = stock_reserved + :qty
            WHERE product_id = :pid
              AND stock_on_hand IS NOT NULL
              AND (stock_on_hand - stock_reserved) >= :qty
            """, nativeQuery = true)
    int increaseReservedStock(@Param("pid") long productId, @Param("qty") int quantity);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE products
            SET stock_reserved = stock_reserved - :qty
            WHERE product_id = :pid
              AND stock_on_hand IS NOT NULL
              AND stock_reserved >= :qty
            """, nativeQuery = true)
    int decreaseReservedStock(@Param("pid") long productId, @Param("qty") int quantity);

    /**
     * Finalize paid order: move units from reserved bucket to sold (decrease on-hand and reserved).
     */
    @Modifying
    @Transactional
    @Query(value = """
            UPDATE products
            SET stock_on_hand = stock_on_hand - :qty,
                stock_reserved = stock_reserved - :qty
            WHERE product_id = :pid
              AND stock_on_hand IS NOT NULL
              AND stock_reserved >= :qty
              AND stock_on_hand >= :qty
            """, nativeQuery = true)
    int consumeReservedAndDecreaseOnHand(@Param("pid") long productId, @Param("qty") int quantity);
}
