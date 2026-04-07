package com.spring.jwt.EmployeeAttendance;

import com.spring.jwt.Enums.AttendanceStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class EmployeeAttendanceDTO {

    private Long attendanceId;

    @Schema(example = "EMP-1001")
    private String employeeCode;

    @Schema(example = "Rahul Patil")
    private String employeeName;

    @Schema(example = "2026-01-11")
    private LocalDate date;

    @NotNull(message = "Attendance status is required")
    @Schema(example = "PRESENT")
    private AttendanceStatus attendanceStatus;

    @Schema(
            description = "Reason required for ABSENT or LEAVE",
            example = "Sick leave"
    )
    private String reason;

    private Boolean attendanceStatusApproval;

    @Schema(example = "10005")
    private Long userId;
}
