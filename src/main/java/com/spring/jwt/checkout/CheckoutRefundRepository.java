package com.spring.jwt.checkout;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CheckoutRefundRepository extends JpaRepository<CheckoutRefund, Long> {

    @Query("SELECT r FROM CheckoutRefund r WHERE r.status IN :statuses")
    List<CheckoutRefund> findByStatusIn(@Param("statuses") Collection<CheckoutRefundRecordStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM CheckoutRefund r JOIN FETCH r.order WHERE r.id = :id")
    Optional<CheckoutRefund> findByIdForUpdateWithOrder(@Param("id") Long id);

    List<CheckoutRefund> findByOrder_IdAndStatus(Long orderId, CheckoutRefundRecordStatus status);

    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM CheckoutRefund r WHERE r.order.id = :orderId AND r.status = 'SUCCESS'")
    java.math.BigDecimal sumSuccessfulAmountByOrderId(@Param("orderId") Long orderId);

    Optional<CheckoutRefund> findFirstByOrder_IdOrderByIdDesc(Long orderId);

    List<CheckoutRefund> findByOrder_IdAndStatusIn(Long orderId, Collection<CheckoutRefundRecordStatus> statuses);

    List<CheckoutRefund> findAllByOrderByCreatedAtDesc();
}
