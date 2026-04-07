package com.spring.jwt.Attendance.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Monthly Attendance Response DTO
 * Used to return monthly attendance summary
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyAttendanceResponse {

    private EmployeeInfo employee;
    private List<AttendanceResponse> records;
    private AttendanceSummary summary;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EmployeeInfo {
        private Long userId;
        private String firstName;
        private String lastName;
        private String employeeCode;
        private String email;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AttendanceSummary {
        private int totalDays;
        private int presentCount;
        private int absentCount;
        private int leaveCount;
        private int halfDayCount;
        private int workFromHomeCount;
        private double totalWorkingHours;
        private double averageWorkingHours;
    }
}
