package com.spring.jwt.ledger;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Append-only financial ledger service.
 * All write methods are idempotent — duplicate calls with same key are no-ops.
 */
public interface LedgerService {

    /**
     * Record a payment entry (+amount).
     * Idempotency key: PAYMENT-{orderId}-{gatewayTrackingId}
     */
    void recordPayment(Long orderId, Long paymentId, BigDecimal amount,
                       String gatewayTrackingId, LedgerSource source,
                       LedgerEntryStatus status, String metadata);

    /**
     * Record a refund entry (-amount).
     * Idempotency key: REFUND-{orderId}-{refundId}
     */
    void recordRefund(Long orderId, Long refundId, BigDecimal amount,
                      String gatewayTrackingId, LedgerSource source,
                      LedgerEntryStatus status, String metadata);

    /**
     * Record an adjustment entry (±amount).
     * Idempotency key: ADJ-{orderId}-{timestamp}
     */
    void recordAdjustment(Long orderId, BigDecimal amount, String reason,
                          LedgerSource source, String metadata);

    /** All ledger entries for a specific order. */
    List<LedgerEntryDTO> findByOrderId(Long orderId);

    /** Paginated, filtered listing. */
    Page<LedgerEntryDTO> findAll(Long orderId, LedgerEntryType type, LedgerEntryStatus status,
                                 LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    /** Summary metrics for dashboard. */
    LedgerSummaryDTO getSummary();
}
