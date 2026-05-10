package com.spring.jwt.checkout;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CheckoutRefundAuditLogRepository extends JpaRepository<CheckoutRefundAuditLog, Long> {

    List<CheckoutRefundAuditLog> findByRefundIdOrderByCreatedAtAsc(Long refundId);
}
