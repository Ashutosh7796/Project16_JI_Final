package com.spring.jwt.Payment;

import com.spring.jwt.entity.PaymentAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentAuditLogRepository extends JpaRepository<PaymentAuditLog, Long> {
    List<PaymentAuditLog> findByPaymentIdAndPaymentTypeOrderByActionAtDesc(Long paymentId, String paymentType);
    List<PaymentAuditLog> findByCcavenueOrderIdOrderByActionAtDesc(String ccavenueOrderId);
}
