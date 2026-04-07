package com.spring.jwt.Attendance.model;

import com.spring.jwt.Attendance.model.embedded.Location;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Attendance Entity
 * Represents employee attendance records with location tracking
 */
@Entity
@Table(name = "attendance", indexes = {
    @Index(name = "idx_user_date", columnList = "user_id, date", unique = true),
    @Index(name = "idx_date", columnList = "date"),
    @Index(name = "idx_user_id", columnList = "user_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attendance_id")
    private Long attendanceId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(name = "attendance_status", nullable = false, length = 20)
    private AttendanceStatus attendanceStatus;

    @Column(name = "check_in_time")
    private LocalTime checkInTime;

    @Column(name = "check_out_time")
    private LocalTime checkOutTime;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "latitude", column = @Column(name = "check_in_latitude")),
        @AttributeOverride(name = "longitude", column = @Column(name = "check_in_longitude")),
        @AttributeOverride(name = "address", column = @Column(name = "check_in_address", length = 500))
    })
    private Location checkInLocation;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "latitude", column = @Column(name = "check_out_latitude")),
        @AttributeOverride(name = "longitude", column = @Column(name = "check_out_longitude")),
        @AttributeOverride(name = "address", column = @Column(name = "check_out_address", length = 500))
    })
    private Location checkOutLocation;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @Column(name = "total_hours")
    private Double totalHours;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    /**
     * Calculate total working hours between check-in and check-out
     */
    public void calculateTotalHours() {
        if (checkInTime != null && checkOutTime != null) {
            long minutes = java.time.Duration.between(checkInTime, checkOutTime).toMinutes();
            this.totalHours = minutes / 60.0;
        }
    }

    /**
     * Check if employee has checked in
     */
    public boolean isCheckedIn() {
        return checkInTime != null;
    }

    /**
     * Check if employee has checked out
     */
    public boolean isCheckedOut() {
        return checkOutTime != null;
    }
}
