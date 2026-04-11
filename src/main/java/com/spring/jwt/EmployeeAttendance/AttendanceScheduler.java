package com.spring.jwt.EmployeeAttendance;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component("employeeAttendanceScheduler")
@RequiredArgsConstructor
public class AttendanceScheduler {

    private final EmployeeAttendanceService attendanceService;

    /**
     * 23:58 — staggered from location-based attendance (23:59) to reduce DB contention.
     */
    @Scheduled(cron = "0 58 23 * * ?", zone = "Asia/Kolkata")
    @SchedulerLock(name = "employeeAttendanceMarkAbsent", lockAtLeastFor = "30s", lockAtMostFor = "25m")
    public void markDailyAbsent() {
        attendanceService.autoMarkAbsentForDate(LocalDate.now());
    }
}
