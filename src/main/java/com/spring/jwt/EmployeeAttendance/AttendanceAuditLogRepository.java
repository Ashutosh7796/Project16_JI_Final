package com.spring.jwt.EmployeeAttendance;


import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceAuditLogRepository
        extends JpaRepository<AttendanceAuditLog, Long> {
}
