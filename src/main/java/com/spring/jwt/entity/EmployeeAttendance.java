package com.spring.jwt.entity;

import com.spring.jwt.Enums.AttendanceStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
@Entity
@Table(
        name = "employee_attendance",
        uniqueConstraints = {
                @UniqueConstraint(
                        columnNames = {"user_id", "attendance_date"}
                )
        },
        indexes = {
                @Index(name = "idx_attendance_emp", columnList = "employee_code"),
                @Index(name = "idx_attendance_date", columnList = "attendance_date"),
                @Index(name = "idx_attendance_status", columnList = "attendance_status")
        }
)
@Data
public class EmployeeAttendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long attendanceId;

    @Column(name = "employee_code", nullable = false)
    private String employeeCode;

    @Column(name = "employee_name", nullable = false)
    private String employeeName;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(name = "attendance_status", nullable = false)
    private AttendanceStatus attendanceStatus;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(nullable = false)
    private Boolean attendanceStatusApproval = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
