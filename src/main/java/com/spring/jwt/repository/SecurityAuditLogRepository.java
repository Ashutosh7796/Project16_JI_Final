package com.spring.jwt.repository;

import com.spring.jwt.entity.SecurityAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Repository
public interface SecurityAuditLogRepository extends JpaRepository<SecurityAuditLog, Long> {

    /** Find audit entries by username (most recent first) */
    Page<SecurityAuditLog> findByUsernameOrderByCreatedAtDesc(String username, Pageable pageable);

    /** Find audit entries by event type */
    Page<SecurityAuditLog> findByEventTypeOrderByCreatedAtDesc(String eventType, Pageable pageable);

    /** Find audit entries by IP (for detecting brute force) */
    List<SecurityAuditLog> findByClientIpAndEventTypeAndCreatedAtAfter(
            String clientIp, String eventType, Instant after);

    /** Count failures from an IP in a window (brute force detection) */
    @Query("SELECT COUNT(a) FROM SecurityAuditLog a WHERE a.clientIp = :ip AND a.eventType = :eventType AND a.success = false AND a.createdAt > :since")
    long countFailuresByIpSince(String ip, String eventType, Instant since);

    /** Purge old audit entries (retention policy) */
    @Modifying
    @Transactional
    @Query("DELETE FROM SecurityAuditLog a WHERE a.createdAt < :before")
    int deleteOlderThan(Instant before);
}
