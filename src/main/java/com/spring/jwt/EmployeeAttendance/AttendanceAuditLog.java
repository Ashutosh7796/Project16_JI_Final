package com.spring.jwt.EmployeeAttendance;


import com.spring.jwt.Enums.AttendanceStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(
        name = "attendance_audit_log",
        indexes = {
                @Index(name = "idx_audit_user", columnList = "user_id"),
                @Index(name = "idx_audit_date", columnList = "attendance_date"),
                @Index(name = "idx_audit_action", columnList = "action_type")
        }
)
public class AttendanceAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Employee whose attendance changed
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    // OLD VALUES
    @Enumerated(EnumType.STRING)
    private AttendanceStatus oldStatus;

    private String oldReason;

    // NEW VALUES
    @Enumerated(EnumType.STRING)
    private AttendanceStatus newStatus;

    private String newReason;

    // ACTION TYPE
    @Column(nullable = false)
    private String actionType;
    // AUTO_ABSENT | ADMIN_OVERRIDE | APPROVED | REJECTED | MANUAL_EDIT

    // WHO DID THE ACTION
    @Column(nullable = false)
    private Long actionByUserId;

    // WHEN
    @Column(nullable = false)
    private LocalDateTime actionAt = LocalDateTime.now();
}
