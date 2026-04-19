package com.spring.jwt.checkout;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckoutRefundAuditLogRepository extends JpaRepository<CheckoutRefundAuditLog, Long> {
}
