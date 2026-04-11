package com.spring.jwt.Payment;

import com.spring.jwt.entity.PaymentAuditLog;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/payment-audit")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class PaymentAuditController {

    private final PaymentAuditLogRepository auditLogRepository;

    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<PaymentAuditLog>> getByOrderId(@PathVariable String orderId) {
        String sanitizedOrderId = PaymentInputSanitizer.sanitizeOrderId(orderId);
        return ResponseEntity.ok(auditLogRepository.findByCcavenueOrderIdOrderByActionAtDesc(sanitizedOrderId));
    }

    @GetMapping("/payment/{paymentType}/{paymentId}")
    public ResponseEntity<List<PaymentAuditLog>> getByPayment(
            @PathVariable String paymentType,
            @PathVariable Long paymentId) {
        return ResponseEntity.ok(
                auditLogRepository.findByPaymentIdAndPaymentTypeOrderByActionAtDesc(paymentId, paymentType));
    }

    @GetMapping("/recent")
    public ResponseEntity<Page<PaymentAuditLog>> getRecent(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        int boundedSize = Math.min(Math.max(size, 1), 200);
        Page<PaymentAuditLog> result = auditLogRepository.findAll(
                PageRequest.of(Math.max(page, 0), boundedSize, Sort.by(Sort.Direction.DESC, "actionAt")));
        return ResponseEntity.ok(result);
    }
}
