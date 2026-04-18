package com.spring.jwt.Payment;

import com.spring.jwt.entity.PaymentCallbackQueue;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentCallbackQueueRepository extends JpaRepository<PaymentCallbackQueue, Long> {

    Optional<PaymentCallbackQueue> findByStatusToken(String statusToken);

    List<PaymentCallbackQueue> findByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            Collection<String> statuses, LocalDateTime nextAttemptAt, Pageable pageable);

    /**
     * Atomically moves a row to PROCESSING so event-driven and scheduled paths cannot double-process.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PaymentCallbackQueue q set q.status = 'PROCESSING', q.updatedAt = :now where q.id = :id and q.status in ('PENDING','RETRY')")
    int claimForProcessing(@Param("id") Long id, @Param("now") LocalDateTime now);

    /**
     * Recover rows left in PROCESSING after a JVM crash or kill mid-handler (scheduled safety net).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PaymentCallbackQueue q set q.status = 'RETRY', q.nextAttemptAt = :nextAttempt where q.status = 'PROCESSING' and q.updatedAt < :staleBefore")
    int releaseStaleProcessing(@Param("staleBefore") LocalDateTime staleBefore, @Param("nextAttempt") LocalDateTime nextAttempt);
}
