package com.spring.jwt.repository;

import com.spring.jwt.entity.ApplicationAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationAuditLogRepository extends JpaRepository<ApplicationAuditLog, Long> {
}
