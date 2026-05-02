package com.spring.jwt.ledger;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Production-grade, append-only ledger implementation.
 * <p>
 * Key guarantees:
 * <ul>
 *   <li>Idempotent — duplicate keys are silently ignored</li>
 *   <li>Append-only — no update/delete operations exist</li>
 *   <li>Transactional — inherits caller's transaction (REQUIRED) for atomicity with order/payment writes</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerServiceImpl implements LedgerService {

    private final LedgerEntryRepository repository;

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void recordPayment(Long orderId, Long paymentId, BigDecimal amount,
                              String gatewayTrackingId, LedgerSource source,
                              LedgerEntryStatus status, String metadata) {
        String idempotencyKey = "PAYMENT-" + orderId + "-" + sanitize(gatewayTrackingId);
        if (repository.existsByIdempotencyKey(idempotencyKey)) {
            log.debug("Ledger: duplicate payment entry skipped (key={})", idempotencyKey);
            return;
        }

        LedgerEntry entry = LedgerEntry.builder()
                .orderId(orderId)
                .paymentId(paymentId)
                .type(LedgerEntryType.PAYMENT)
                .amount(amount.abs().setScale(2, RoundingMode.HALF_UP))
                .status(status)
                .source(source)
                .gatewayTrackingId(gatewayTrackingId)
                .idempotencyKey(idempotencyKey)
                .metadata(metadata)
                .build();
        repository.save(entry);
        log.info("Ledger: PAYMENT recorded orderId={} paymentId={} amount={} status={} source={}",
                orderId, paymentId, amount, status, source);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void recordRefund(Long orderId, Long refundId, BigDecimal amount,
                             String gatewayTrackingId, LedgerSource source,
                             LedgerEntryStatus status, String metadata) {
        String idempotencyKey = "REFUND-" + orderId + "-" + refundId;
        if (repository.existsByIdempotencyKey(idempotencyKey)) {
            log.debug("Ledger: duplicate refund entry skipped (key={})", idempotencyKey);
            return;
        }

        LedgerEntry entry = LedgerEntry.builder()
                .orderId(orderId)
                .refundId(refundId)
                .type(LedgerEntryType.REFUND)
                .amount(amount.abs().negate().setScale(2, RoundingMode.HALF_UP)) // Always negative for refunds
                .status(status)
                .source(source)
                .gatewayTrackingId(gatewayTrackingId)
                .idempotencyKey(idempotencyKey)
                .metadata(metadata)
                .build();
        repository.save(entry);
        log.info("Ledger: REFUND recorded orderId={} refundId={} amount={} status={} source={}",
                orderId, refundId, amount.negate(), status, source);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void recordAdjustment(Long orderId, BigDecimal amount, String reason,
                                 LedgerSource source, String metadata) {
        String idempotencyKey = "ADJ-" + orderId + "-" + System.currentTimeMillis();
        // Adjustments are rare and timestamp-keyed — no dedup check needed (unique per call)

        LedgerEntry entry = LedgerEntry.builder()
                .orderId(orderId)
                .type(LedgerEntryType.ADJUSTMENT)
                .amount(amount.setScale(2, RoundingMode.HALF_UP))
                .status(LedgerEntryStatus.SUCCESS)
                .source(source)
                .idempotencyKey(idempotencyKey)
                .metadata(metadata != null ? metadata : reason)
                .build();
        repository.save(entry);
        log.info("Ledger: ADJUSTMENT recorded orderId={} amount={} reason={} source={}",
                orderId, amount, reason, source);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LedgerEntryDTO> findByOrderId(Long orderId) {
        return repository.findByOrderIdOrderByCreatedAtDesc(orderId)
                .stream()
                .map(LedgerEntryDTO::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LedgerEntryDTO> findAll(Long orderId, LedgerEntryType type, LedgerEntryStatus status,
                                         LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        return repository.findFiltered(orderId, type, status, startDate, endDate, pageable)
                .map(LedgerEntryDTO::from);
    }

    @Override
    @Transactional(readOnly = true)
    public LedgerSummaryDTO getSummary() {
        BigDecimal collected = repository.sumTotalCollected().setScale(2, RoundingMode.HALF_UP);
        BigDecimal refunded = repository.sumTotalRefunded().setScale(2, RoundingMode.HALF_UP);
        BigDecimal net = collected.subtract(refunded);

        long totalPayments = repository.countByTypeAndStatus(LedgerEntryType.PAYMENT, LedgerEntryStatus.SUCCESS);
        long totalRefunds = repository.countByTypeAndStatus(LedgerEntryType.REFUND, LedgerEntryStatus.SUCCESS);
        long failed = repository.countByTypeAndStatus(LedgerEntryType.PAYMENT, LedgerEntryStatus.FAILED)
                    + repository.countByTypeAndStatus(LedgerEntryType.REFUND, LedgerEntryStatus.FAILED);

        return LedgerSummaryDTO.builder()
                .totalCollected(collected)
                .totalRefunded(refunded)
                .netAmount(net)
                .totalPayments(totalPayments)
                .totalRefunds(totalRefunds)
                .failedTransactions(failed)
                .build();
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) return "UNKNOWN";
        return value.trim().replaceAll("[^a-zA-Z0-9\\-_]", "");
    }
}
