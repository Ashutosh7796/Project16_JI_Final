package com.spring.jwt.EmployeeAttendance;

import com.spring.jwt.Enums.AttendanceStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class AdminAttendanceOverrideDTO {

    @NotNull
    private Long userId;              // employee userId

    @NotNull
    private LocalDate date;            // attendance date to override

    @NotNull
    private AttendanceStatus status;   // PRESENT / ABSENT / LEAVE

    private String reason;             // optional for PRESENT
}
