package com.spring.jwt.Attendance.dto.request;

import com.spring.jwt.Attendance.model.AttendanceStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Check-In Request DTO
 * Used when employee marks attendance (check-in)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckInRequest {

    private Long userId;

    @NotNull(message = "Date is required")
    @PastOrPresent(message = "Date cannot be in the future")
    private LocalDate date;

    @NotNull(message = "Attendance status is required")
    private AttendanceStatus attendanceStatus;

    @Valid
    @NotNull(message = "Location is required for check-in")
    private LocationDTO location;

    private String remarks;
}
