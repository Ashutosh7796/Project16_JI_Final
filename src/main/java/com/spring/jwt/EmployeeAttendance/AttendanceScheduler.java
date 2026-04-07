package com.spring.jwt.EmployeeAttendance;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@EnableScheduling
@Component("employeeAttendanceScheduler")
@RequiredArgsConstructor
public class AttendanceScheduler {

    private final EmployeeAttendanceService attendanceService;

    @Scheduled(cron = "0 59 23 * * ?")
    public void markDailyAbsent() {
        attendanceService
                .autoMarkAbsentForDate(LocalDate.now());
    }
}
