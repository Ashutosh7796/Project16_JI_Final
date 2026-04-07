package com.spring.jwt.EmployeeAttendance;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AttendanceDashboardDTO {

    private long present;
    private long absent;
    private long leave;
}
