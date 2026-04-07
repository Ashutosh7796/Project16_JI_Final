package com.spring.jwt.Attendance.service;

import com.spring.jwt.Attendance.dto.request.CheckInRequest;
import com.spring.jwt.Attendance.dto.request.CheckOutRequest;
import com.spring.jwt.Attendance.dto.request.LeaveRequestDTO;
import com.spring.jwt.Attendance.dto.response.AttendanceResponse;
import com.spring.jwt.Attendance.dto.response.LeaveRequestResponse;
import com.spring.jwt.Attendance.dto.response.MonthlyAttendanceResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

/**
 * Attendance Service Interface
 * Business logic for attendance operations
 */
public interface AttendanceService {

    /**
     * Mark attendance (check-in)
     * @param request Check-in request with location
     * @return Attendance response
     */
    AttendanceResponse checkIn(CheckInRequest request);

    /**
     * Check out
     * @param request Check-out request with location
     * @return Updated attendance response
     */
    AttendanceResponse checkOut(CheckOutRequest request);

    /**
     * Get today's attendance for a user
     * @param userId User ID
     * @return Attendance response or null if not found
     */
    AttendanceResponse getTodayAttendance(Long userId);

    /**
     * Get attendance for a specific date
     * @param userId User ID
     * @param date Date
     * @return Attendance response or null if not found
     */
    AttendanceResponse getAttendanceByDate(Long userId, LocalDate date);

    /**
     * Get monthly attendance summary for a user
     * @param userId User ID
     * @param year Year
     * @param month Month (1-12)
     * @return Monthly attendance response
     */
    MonthlyAttendanceResponse getMonthlyAttendance(Long userId, int year, int month);

    /**
     * Get attendance records for a date range
     * @param userId User ID
     * @param startDate Start date
     * @param endDate End date
     * @return List of attendance responses
     */
    List<AttendanceResponse> getAttendanceByDateRange(Long userId, LocalDate startDate, LocalDate endDate);

    /**
     * Check if user has already checked in today
     * @param userId User ID
     * @param date Date
     * @return true if checked in, false otherwise
     */
    boolean hasCheckedIn(Long userId, LocalDate date);

    /**
     * Check if user has already checked out today
     * @param userId User ID
     * @param date Date
     * @return true if checked out, false otherwise
     */
    boolean hasCheckedOut(Long userId, LocalDate date);

    /**
     * Auto check-out for employees who forgot to check out
     * @param date Date to process
     * @return Number of records updated
     */
    int autoCheckOut(LocalDate date);

    /**
     * Get monthly attendance for all employees (Admin)
     * @param year Year
     * @param month Month (1-12)
     * @return List of monthly attendance responses for all employees
     */
    List<MonthlyAttendanceResponse> getAllEmployeesMonthlyAttendance(int year, int month);


    /**
     * Request leave
     * @param request Leave request DTO
     * @return Leave request response
     */
    LeaveRequestResponse requestLeave(LeaveRequestDTO request);

    /**
     * Get all leave requests (paginated)
     * @param pageable Pagination info
     * @return Page of leave requests
     */
    Page<LeaveRequestResponse> getAllLeaveRequests(Pageable pageable);

    /**
     * Get leave requests by status
     * @param status Status (PENDING, APPROVED, REJECTED)
     * @param pageable Pagination info
     * @return Page of leave requests
     */
    Page<LeaveRequestResponse> getLeaveRequestsByStatus(String status, Pageable pageable);

    /**
     * Get my leave requests
     * @param userId User ID
     * @param pageable Pagination info
     * @return Page of leave requests
     */
    Page<LeaveRequestResponse> getMyLeaveRequests(Long userId, Pageable pageable);

    /**
     * Approve leave request
     * @param leaveRequestId Leave request ID
     * @param approvedBy Admin user ID
     * @return Updated leave request response
     */
    LeaveRequestResponse approveLeaveRequest(Long leaveRequestId, Long approvedBy);

    /**
     * Reject leave request
     * @param leaveRequestId Leave request ID
     * @param rejectionReason Reason for rejection
     * @param rejectedBy Admin user ID
     * @return Updated leave request response
     */
    LeaveRequestResponse rejectLeaveRequest(Long leaveRequestId, String rejectionReason, Long rejectedBy);
}
