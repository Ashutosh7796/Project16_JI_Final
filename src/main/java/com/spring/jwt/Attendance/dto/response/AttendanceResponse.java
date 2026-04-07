package com.spring.jwt.Attendance.dto.response;

import com.spring.jwt.Attendance.model.AttendanceStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Attendance Response DTO
 * Used to return attendance information to frontend
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceResponse {

    private Long attendanceId;
    private Long userId;
    
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;
    
    private AttendanceStatus attendanceStatus;
    
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime checkInTime;
    
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime checkOutTime;
    
    private LocationResponse checkInLocation;
    private LocationResponse checkOutLocation;
    
    private String remarks;
    private Double totalHours;
    
    private String day;

    private boolean checkedIn;
    private boolean checkedOut;
}
