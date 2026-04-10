package com.spring.jwt.Payment;

import com.spring.jwt.entity.PaymentCallbackQueue;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface PaymentCallbackQueueRepository extends JpaRepository<PaymentCallbackQueue, Long> {

    List<PaymentCallbackQueue> findByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            Collection<String> statuses, LocalDateTime nextAttemptAt, Pageable pageable);
}
