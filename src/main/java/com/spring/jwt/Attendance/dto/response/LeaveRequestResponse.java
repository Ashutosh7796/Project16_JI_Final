package com.spring.jwt.Attendance.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Leave Request Response DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveRequestResponse {

    private Long leaveRequestId;
    private Long userId;
    private String employeeName;
    private String leaveType;
    
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;
    
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;
    
    private Integer totalDays;
    private String reason;
    private String status;
    private String rejectionReason;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime requestedAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime respondedAt;
    
    private Long respondedBy;
}
