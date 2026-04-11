package com.spring.jwt.EmployeeAttendance;

import com.spring.jwt.Enums.AttendanceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface EmployeeAttendanceService {

    EmployeeAttendanceDTO markAttendance(EmployeeAttendanceDTO dto);

    EmployeeAttendanceDTO getAttendanceById(Long attendanceId);

    Page<EmployeeAttendanceDTO> getAllAttendance(Pageable pageable);

    Page<EmployeeAttendanceDTO> getMyAttendance(Pageable pageable);

    Page<EmployeeAttendanceDTO> getAttendanceByStatus(
            AttendanceStatus status, Pageable pageable);

    Page<EmployeeAttendanceDTO> getMyAttendanceByStatus(
            AttendanceStatus status, Pageable pageable);

  //ADMIN ACTIONS
    EmployeeAttendanceDTO approveAttendance(Long attendanceId);

    EmployeeAttendanceDTO rejectAttendance(Long attendanceId, String reason);

    // DASHBOARD
    AttendanceDashboardDTO getAttendanceDashboardCount();

    // MONTHLY SUMMARY
    MonthlyAttendanceSummaryDTO getMyMonthlyAttendanceSummary(
            int month, int year);

    void deleteAttendance(Long attendanceId);

    EmployeeMonthlyAttendanceReportDTO
    getMyMonthlyAttendanceFullReport(int month, int year);

    public void autoMarkAbsentForDate(LocalDate date);

    EmployeeAttendanceDTO adminOverrideAttendance(
            AdminAttendanceOverrideDTO dto);

}
