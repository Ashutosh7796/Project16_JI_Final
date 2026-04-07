package com.spring.jwt.Attendance.controller;

import com.spring.jwt.Attendance.dto.response.AttendanceResponse;
import com.spring.jwt.Attendance.dto.response.MonthlyAttendanceResponse;
import com.spring.jwt.Attendance.service.AttendanceService;
import com.spring.jwt.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Admin Attendance Controller
 * REST API endpoints for admin to manage and view employee attendance
 */
@RestController
@RequestMapping("/api/v1/admin/attendance")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Attendance", description = "Admin endpoints for employee attendance management")
public class AdminAttendanceController {

    private final AttendanceService attendanceService;

    /**
     * Get all employees' attendance summary for admin dashboard
     * GET /api/v1/admin/attendance/all-employees?month=2&year=2026
     */
    @GetMapping("/all-employees")
    @Operation(summary = "Get all employees attendance (Admin)", description = "Get attendance summary for all employees for a specific month")
    public ResponseEntity<ApiResponse<List<MonthlyAttendanceResponse>>> getAllEmployeesAttendance(
            @RequestParam int month,
            @RequestParam int year) {
        
        log.info("Admin fetching attendance for all employees - {}/{}", year, month);
        
        List<MonthlyAttendanceResponse> response = attendanceService.getAllEmployeesMonthlyAttendance(year, month);
        
        return ResponseEntity.ok(ApiResponse.success("All employees attendance retrieved successfully", response));
    }

    /**
     * Get employee attendance with location history for admin dashboard
     * GET /api/v1/admin/attendance/employee/{userId}?month=2&year=2026
     */
    @GetMapping("/employee/{userId}")
    @Operation(summary = "Get employee attendance (Admin)", description = "Get employee attendance with location history for a specific month")
    public ResponseEntity<ApiResponse<MonthlyAttendanceResponse>> getEmployeeAttendance(
            @PathVariable Long userId,
            @RequestParam int month,
            @RequestParam int year) {
        
        log.info("Admin fetching attendance for employee: {} - {}/{}", userId, year, month);
        
        MonthlyAttendanceResponse response = attendanceService.getMonthlyAttendance(userId, year, month);
        
        if (response == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(HttpStatus.NOT_FOUND, "No attendance records found for employee in the specified month", null));
        }
        
        return ResponseEntity.ok(ApiResponse.success("Employee attendance retrieved successfully", response));
    }

    /**
     * Get employee attendance for date range (Admin)
     * GET /api/v1/admin/attendance/employee/{userId}/range?startDate=2026-02-01&endDate=2026-02-28
     */
    @GetMapping("/employee/{userId}/range")
    @Operation(summary = "Get employee attendance by date range (Admin)", description = "Get employee attendance records for a date range")
    public ResponseEntity<ApiResponse<List<AttendanceResponse>>> getEmployeeAttendanceByDateRange(
            @PathVariable Long userId,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {
        
        log.info("Admin fetching attendance for employee: {} from {} to {}", userId, startDate, endDate);
        
        List<AttendanceResponse> response = attendanceService.getAttendanceByDateRange(userId, startDate, endDate);
        
        return ResponseEntity.ok(ApiResponse.success("Employee attendance records retrieved successfully", response));
    }

    /**
     * Get employee attendance for specific date (Admin)
     * GET /api/v1/admin/attendance/employee/{userId}/date/{date}
     */
    @GetMapping("/employee/{userId}/date/{date}")
    @Operation(summary = "Get employee attendance by date (Admin)", description = "Get employee attendance for a specific date")
    public ResponseEntity<ApiResponse<AttendanceResponse>> getEmployeeAttendanceByDate(
            @PathVariable Long userId,
            @PathVariable LocalDate date) {
        
        log.info("Admin fetching attendance for employee: {} on date: {}", userId, date);
        
        AttendanceResponse response = attendanceService.getAttendanceByDate(userId, date);
        
        if (response == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(HttpStatus.NOT_FOUND, "No attendance record found for employee on the specified date", null));
        }
        
        return ResponseEntity.ok(ApiResponse.success("Employee attendance retrieved successfully", response));
    }
}
