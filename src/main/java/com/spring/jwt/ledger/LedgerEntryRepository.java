package com.spring.jwt.ledger;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    /** Check idempotency before insert. */
    boolean existsByIdempotencyKey(String idempotencyKey);

    Optional<LedgerEntry> findByIdempotencyKey(String idempotencyKey);

    /** All entries for a specific order (newest first). */
    List<LedgerEntry> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    /** Paginated listing with optional filters. All params nullable — null means no filter. */
    @Query("SELECT e FROM LedgerEntry e WHERE " +
           "(:orderId IS NULL OR e.orderId = :orderId) AND " +
           "(:type IS NULL OR e.type = :type) AND " +
           "(:status IS NULL OR e.status = :status) AND " +
           "(:startDate IS NULL OR e.createdAt >= :startDate) AND " +
           "(:endDate IS NULL OR e.createdAt <= :endDate) " +
           "ORDER BY e.createdAt DESC")
    Page<LedgerEntry> findFiltered(
            @Param("orderId") Long orderId,
            @Param("type") LedgerEntryType type,
            @Param("status") LedgerEntryStatus status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    /** Sum of all successful payment amounts (positive inflows). */
    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM LedgerEntry e WHERE e.type = 'PAYMENT' AND e.status = 'SUCCESS'")
    BigDecimal sumTotalCollected();

    /** Sum of all successful refund amounts (negative outflows — returns absolute value). */
    @Query("SELECT COALESCE(SUM(ABS(e.amount)), 0) FROM LedgerEntry e WHERE e.type = 'REFUND' AND e.status = 'SUCCESS'")
    BigDecimal sumTotalRefunded();

    /** Count entries by type for dashboard metrics. */
    long countByTypeAndStatus(LedgerEntryType type, LedgerEntryStatus status);
}
