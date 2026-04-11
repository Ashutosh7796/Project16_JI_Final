package com.spring.jwt.Attendance.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Leave Request Entity
 */
@Entity
@Table(name = "leave_requests", indexes = {
    @Index(name = "idx_leave_user", columnList = "user_id"),
    @Index(name = "idx_leave_status", columnList = "status"),
    @Index(name = "idx_leave_dates", columnList = "start_date, end_date")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "leave_request_id")
    private Long leaveRequestId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "employee_name", nullable = false)
    private String employeeName;

    @Column(name = "leave_type", nullable = false, length = 50)
    private String leaveType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "total_days", nullable = false)
    private Integer totalDays;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING"; // PENDING, APPROVED, REJECTED

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @CreationTimestamp
    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "responded_by")
    private Long respondedBy;

    /**
     * Calculate total days between start and end date
     */
    @PrePersist
    @PreUpdate
    public void calculateTotalDays() {
        if (startDate != null && endDate != null) {
            this.totalDays = (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
        }
    }
}
