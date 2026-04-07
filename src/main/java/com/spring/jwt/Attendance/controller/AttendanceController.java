package com.spring.jwt.Attendance.controller;

import com.spring.jwt.Attendance.dto.request.CheckInRequest;
import com.spring.jwt.Attendance.dto.request.CheckOutRequest;
import com.spring.jwt.Attendance.dto.request.LeaveRequestDTO;
import com.spring.jwt.Attendance.dto.response.AttendanceResponse;
import com.spring.jwt.Attendance.dto.response.LeaveRequestResponse;
import com.spring.jwt.Attendance.dto.response.MonthlyAttendanceResponse;
import com.spring.jwt.Attendance.service.AttendanceService;
import com.spring.jwt.utils.ApiResponse;
import com.spring.jwt.utils.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Attendance Controller
 * REST API endpoints for employee attendance operations
 */
@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Attendance", description = "Employee attendance management APIs")
public class AttendanceController {

    private final AttendanceService attendanceService;

    /**
     * Mark attendance (Check-in)
     * POST /api/v1/attendance/mark
     */
    @PostMapping("/mark")
    @Operation(summary = "Check-in", description = "Mark attendance with location for the day")
    public ResponseEntity<ApiResponse<AttendanceResponse>> checkIn(
            @Valid @RequestBody CheckInRequest request,
            Authentication authentication) {

        Long authenticatedUserId = getUserIdFromAuth(authentication);
        
        log.info("Check-in request received for user: {}", authenticatedUserId);

        request.setUserId(authenticatedUserId);
        
        AttendanceResponse response = attendanceService.checkIn(request);
        
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Check-in successful", response));
    }

    /**
     * Check-out
     * POST /api/v1/attendance/checkout
     */
    @PostMapping("/checkout")
    @Operation(summary = "Check-out", description = "Check-out with location")
    public ResponseEntity<ApiResponse<AttendanceResponse>> checkOut(
            @Valid @RequestBody CheckOutRequest request,
            Authentication authentication) {

        Long authenticatedUserId = getUserIdFromAuth(authentication);
        
        log.info("Check-out request received for user: {}", authenticatedUserId);

        request.setUserId(authenticatedUserId);
        
        AttendanceResponse response = attendanceService.checkOut(request);
        
        return ResponseEntity.ok(ApiResponse.success("Check-out successful", response));
    }

    /**
     * Get today's attendance for logged-in user
     * GET /api/v1/attendance/me
     */
    @GetMapping("/me")
    @Operation(summary = "Get today's attendance", description = "Get attendance record for today")
    public ResponseEntity<ApiResponse<AttendanceResponse>> getTodayAttendance(
            Authentication authentication) {
        
        Long userId = getUserIdFromAuth(authentication);
        log.debug("Fetching today's attendance for user: {}", userId);
        
        AttendanceResponse response = attendanceService.getTodayAttendance(userId);
        
        if (response == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(HttpStatus.NOT_FOUND, "No attendance record found for today", null));
        }
        
        return ResponseEntity.ok(ApiResponse.success("Attendance retrieved successfully", response));
    }

    /**
     * Get attendance for specific date
     * GET /api/v1/attendance/me/date/{date}
     */
    @GetMapping("/me/date/{date}")
    @Operation(summary = "Get attendance by date", description = "Get attendance record for specific date")
    public ResponseEntity<ApiResponse<AttendanceResponse>> getAttendanceByDate(
            @PathVariable LocalDate date,
            Authentication authentication) {
        
        Long userId = getUserIdFromAuth(authentication);
        log.debug("Fetching attendance for user: {} on date: {}", userId, date);
        
        AttendanceResponse response = attendanceService.getAttendanceByDate(userId, date);
        
        if (response == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(HttpStatus.NOT_FOUND, "No attendance record found for the specified date", null));
        }
        
        return ResponseEntity.ok(ApiResponse.success("Attendance retrieved successfully", response));
    }

    /**
     * Get monthly attendance summary
     * GET /api/v1/attendance/me/monthly-summary?month=2&year=2026
     */
    @GetMapping("/me/monthly-summary")
    @Operation(summary = "Get monthly attendance", description = "Get attendance summary for a specific month")
    public ResponseEntity<ApiResponse<MonthlyAttendanceResponse>> getMonthlyAttendance(
            @RequestParam int month,
            @RequestParam int year,
            Authentication authentication) {
        
        Long userId = getUserIdFromAuth(authentication);
        log.debug("Fetching monthly attendance for user: {} - {}/{}", userId, year, month);
        
        MonthlyAttendanceResponse response = attendanceService.getMonthlyAttendance(userId, year, month);
        
        if (response == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(HttpStatus.NOT_FOUND, "No attendance records found for the specified month", null));
        }
        
        return ResponseEntity.ok(ApiResponse.success("Monthly attendance retrieved successfully", response));
    }

    /**
     * Get monthly attendance report
     * GET /api/v1/attendance/me/monthly-report?month=2&year=2026
     */
    @GetMapping("/me/monthly-report")
    @Operation(summary = "Get monthly attendance report", description = "Get detailed attendance report for a specific month")
    public ResponseEntity<ApiResponse<MonthlyAttendanceResponse>> getMonthlyAttendanceReport(
            @RequestParam int month,
            @RequestParam int year,
            Authentication authentication) {
        
        Long userId = getUserIdFromAuth(authentication);
        log.info("Fetching monthly attendance report for user: {} - {}/{}", userId, year, month);
        log.info("Authentication principal: {}", authentication.getPrincipal().getClass().getName());
        
        MonthlyAttendanceResponse response = attendanceService.getMonthlyAttendance(userId, year, month);
        
        if (response == null) {
            log.warn("No attendance data found for user: {} in {}/{}", userId, year, month);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(HttpStatus.NOT_FOUND, "No attendance records found for the specified month", null));
        }
        
        return ResponseEntity.ok(ApiResponse.success("Monthly attendance report retrieved successfully", response));
    }

    /**
     * Get attendance for date range
     * GET /api/v1/attendance/me/range?startDate=2026-02-01&endDate=2026-02-28
     */
    @GetMapping("/me/range")
    @Operation(summary = "Get attendance by date range", description = "Get attendance records for a date range")
    public ResponseEntity<ApiResponse<List<AttendanceResponse>>> getAttendanceByDateRange(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate,
            Authentication authentication) {
        
        Long userId = getUserIdFromAuth(authentication);
        log.debug("Fetching attendance for user: {} from {} to {}", userId, startDate, endDate);
        
        List<AttendanceResponse> response = attendanceService.getAttendanceByDateRange(userId, startDate, endDate);
        
        return ResponseEntity.ok(ApiResponse.success("Attendance records retrieved successfully", response));
    }

    /**
     * Check if user has checked in today
     * GET /api/v1/attendance/me/check-in-status
     */
    @GetMapping("/me/check-in-status")
    @Operation(summary = "Check-in status", description = "Check if user has checked in today")
    public ResponseEntity<ApiResponse<Boolean>> getCheckInStatus(Authentication authentication) {
        
        Long userId = getUserIdFromAuth(authentication);
        boolean hasCheckedIn = attendanceService.hasCheckedIn(userId, LocalDate.now());
        
        return ResponseEntity.ok(ApiResponse.success("Check-in status retrieved", hasCheckedIn));
    }

    /**
     * Request leave
     * POST /api/v1/attendance/leave/request
     */
    @PostMapping("/leave/request")
    @Operation(summary = "Request leave", description = "Submit a leave request")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> requestLeave(
            @Valid @RequestBody LeaveRequestDTO request,
            Authentication authentication) {
        
        Long authenticatedUserId = getUserIdFromAuth(authentication);
        
        log.info("Leave request received from user: {}", authenticatedUserId);
        
        request.setUserId(authenticatedUserId);
        
        LeaveRequestResponse response = attendanceService.requestLeave(request);
        
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Leave request submitted successfully", response));
    }

    /**
     * Get all leave requests (Admin)
     * GET /api/v1/attendance/leave/requests?page=0&size=10
     */
    @GetMapping("/leave/requests")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all leave requests (Admin)", description = "Get all leave requests with pagination")
    public ResponseEntity<ApiResponse<Page<LeaveRequestResponse>>> getAllLeaveRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        log.info("Admin fetching all leave requests");
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("requestedAt").descending());
        Page<LeaveRequestResponse> response = attendanceService.getAllLeaveRequests(pageable);
        
        return ResponseEntity.ok(ApiResponse.success("Leave requests retrieved successfully", response));
    }

    /**
     * Get leave requests by status (Admin)
     * GET /api/v1/attendance/leave/requests/status/{status}?page=0&size=10
     */
    @GetMapping("/leave/requests/status/{status}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get leave requests by status (Admin)", description = "Get leave requests filtered by status")
    public ResponseEntity<ApiResponse<Page<LeaveRequestResponse>>> getLeaveRequestsByStatus(
            @PathVariable String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        log.info("Admin fetching leave requests with status: {}", status);
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("requestedAt").descending());
        Page<LeaveRequestResponse> response = attendanceService.getLeaveRequestsByStatus(status, pageable);
        
        return ResponseEntity.ok(ApiResponse.success("Leave requests retrieved successfully", response));
    }

    /**
     * Get my leave requests
     * GET /api/v1/attendance/leave/my-requests?page=0&size=10
     */
    @GetMapping("/leave/my-requests")
    @Operation(summary = "Get my leave requests", description = "Get leave requests for logged-in user")
    public ResponseEntity<ApiResponse<Page<LeaveRequestResponse>>> getMyLeaveRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        
        Long userId = getUserIdFromAuth(authentication);
        log.debug("Fetching leave requests for user: {}", userId);
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("requestedAt").descending());
        Page<LeaveRequestResponse> response = attendanceService.getMyLeaveRequests(userId, pageable);
        
        return ResponseEntity.ok(ApiResponse.success("Leave requests retrieved successfully", response));
    }

    /**
     * Approve leave request (Admin)
     * PUT /api/v1/attendance/leave/approve/{leaveRequestId}
     */
    @PutMapping("/leave/approve/{leaveRequestId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Approve leave request (Admin)", description = "Approve a pending leave request")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> approveLeaveRequest(
            @PathVariable Long leaveRequestId,
            Authentication authentication) {
        
        Long adminUserId = getUserIdFromAuth(authentication);
        log.info("Admin {} approving leave request: {}", adminUserId, leaveRequestId);
        
        LeaveRequestResponse response = attendanceService.approveLeaveRequest(leaveRequestId, adminUserId);
        
        return ResponseEntity.ok(ApiResponse.success("Leave request approved successfully", response));
    }

    /**
     * Reject leave request (Admin)
     * PUT /api/v1/attendance/leave/reject/{leaveRequestId}
     */
    @PutMapping("/leave/reject/{leaveRequestId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reject leave request (Admin)", description = "Reject a pending leave request")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> rejectLeaveRequest(
            @PathVariable Long leaveRequestId,
            @RequestParam String reason,
            Authentication authentication) {
        
        Long adminUserId = getUserIdFromAuth(authentication);
        log.info("Admin {} rejecting leave request: {}", adminUserId, leaveRequestId);
        
        LeaveRequestResponse response = attendanceService.rejectLeaveRequest(leaveRequestId, reason, adminUserId);
        
        return ResponseEntity.ok(ApiResponse.success("Leave request rejected successfully", response));
    }
    
    private Long getUserIdFromAuth(Authentication authentication) {

        return SecurityUtil.getCurrentUserId();
    }
}
