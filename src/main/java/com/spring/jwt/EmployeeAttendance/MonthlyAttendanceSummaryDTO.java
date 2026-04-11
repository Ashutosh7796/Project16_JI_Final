package com.spring.jwt.EmployeeAttendance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MonthlyAttendanceSummaryDTO {

    private int month;
    private int year;

    private long totalDays;
    private long presentDays;
    private long absentDays;
    private long leaveDays;
}
