package com.spring.jwt.Attendance.scheduler;

import com.spring.jwt.Attendance.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Attendance Scheduler
 * Scheduled tasks for attendance management
 */
@Component("locationBasedAttendanceScheduler")
@RequiredArgsConstructor
@Slf4j
public class AttendanceScheduler {

    private final AttendanceService attendanceService;

    /**
     * Auto check-out employees who forgot to check out
     * Runs every day at 11:59 PM
     */
    @Scheduled(cron = "0 59 23 * * *")
    public void autoCheckoutScheduler() {
        log.info("Running scheduled auto check-out task");
        
        try {
            LocalDate yesterday = LocalDate.now().minusDays(1);
            int count = attendanceService.autoCheckOut(yesterday);
            
            log.info("Auto check-out completed. {} employees checked out automatically", count);
        } catch (Exception e) {
            log.error("Error during auto check-out: ", e);
        }
    }

    /**
     * Send attendance reminder notifications
     * Runs every day at 9:00 AM
     */
    @Scheduled(cron = "0 0 9 * * MON-FRI")
    public void sendAttendanceReminder() {
        log.info("Sending attendance reminder notifications");
    }

    /**
     * Generate daily attendance report
     * Runs every day at 6:00 PM
     */
    @Scheduled(cron = "0 0 18 * * MON-FRI")
    public void generateDailyReport() {
        log.info("Generating daily attendance report");
    }
}
