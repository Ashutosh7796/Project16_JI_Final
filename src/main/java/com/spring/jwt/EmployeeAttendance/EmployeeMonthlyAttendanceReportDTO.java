package com.spring.jwt.EmployeeAttendance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EmployeeMonthlyAttendanceReportDTO {

    private String employeeCode;
    private String employeeName;

    private int month;
    private int year;

    private long presentCount;
    private long absentCount;
    private long leaveCount;

    private List<AttendanceDayReportDTO> dailyReport;
}
