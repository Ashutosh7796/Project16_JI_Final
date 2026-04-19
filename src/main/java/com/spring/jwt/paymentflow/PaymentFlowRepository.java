package com.spring.jwt.paymentflow;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentFlowRepository extends JpaRepository<PaymentFlowEntity, Long> {

    Optional<PaymentFlowEntity> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentFlowEntity p where p.id = :id")
    Optional<PaymentFlowEntity> findByIdForUpdate(@Param("id") Long id);

    Optional<PaymentFlowEntity> findByGatewayTransactionId(String gatewayTransactionId);

    @Query("select p from PaymentFlowEntity p where p.status in :statuses and p.pendingGatewaySince is not null and p.pendingGatewaySince < :cutoff")
    List<PaymentFlowEntity> findStalePendingGateway(@Param("statuses") Collection<PaymentFlowStatus> statuses,
                                                    @Param("cutoff") Instant cutoff);

    @Query("select p from PaymentFlowEntity p where p.status = :status and p.abandonedAt is not null and p.abandonedAt < :cutoff")
    List<PaymentFlowEntity> findStaleAbandoned(@Param("status") PaymentFlowStatus status, @Param("cutoff") Instant cutoff);
}
