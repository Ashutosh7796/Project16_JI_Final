package com.spring.jwt.checkout;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CheckoutOrderRepository extends JpaRepository<CheckoutOrder, Long> {

    Optional<CheckoutOrder> findByMerchantOrderId(String merchantOrderId);

    Optional<CheckoutOrder> findByUserIdAndCheckoutIdempotencyKey(Long userId, String checkoutIdempotencyKey);

    Optional<CheckoutOrder> findByUserIdAndPaymentInitIdempotencyKey(Long userId, String paymentInitIdempotencyKey);

    Page<CheckoutOrder> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM CheckoutOrder o WHERE o.id = :id")
    Optional<CheckoutOrder> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM CheckoutOrder o WHERE o.merchantOrderId = :merchantOrderId")
    Optional<CheckoutOrder> findByMerchantOrderIdForUpdate(@Param("merchantOrderId") String merchantOrderId);

    @Query("SELECT o FROM CheckoutOrder o WHERE o.status = :status AND o.reservationExpiresAt IS NOT NULL AND o.reservationExpiresAt < :now")
    List<CheckoutOrder> findPaymentPendingExpired(@Param("status") CheckoutOrderStatus status, @Param("now") LocalDateTime now);

    @Query("SELECT o FROM CheckoutOrder o WHERE o.status = :status AND o.updatedAt IS NOT NULL AND o.updatedAt < :threshold")
    List<CheckoutOrder> findByStatusAndUpdatedAtBefore(@Param("status") CheckoutOrderStatus status, @Param("threshold") LocalDateTime threshold);

    Optional<CheckoutOrder> findTopByStatusOrderByCreatedAtDesc(CheckoutOrderStatus status);

    Page<CheckoutOrder> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM CheckoutOrder o WHERE o.status = :status")
    Double sumTotalAmountByStatus(@Param("status") CheckoutOrderStatus status);

    long countByStatus(CheckoutOrderStatus status);
    
    @Query("SELECT COUNT(o) FROM CheckoutOrder o")
    long countAllOrders();

    @Query(value = "SELECT DATE(created_at) as sale_date, SUM(total_amount) as total_sales, COUNT(*) as order_count " +
                   "FROM checkout_orders " +
                   "WHERE status NOT IN ('INITIATED', 'FAILED', 'CANCELLED') " +
                   "GROUP BY DATE(created_at) " +
                   "ORDER BY sale_date ASC", nativeQuery = true)
    List<java.util.Map<String, Object>> findSalesTrends();
}
