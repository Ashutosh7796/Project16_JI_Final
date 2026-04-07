package com.spring.jwt.Attendance.service.impl;

import com.spring.jwt.Attendance.dto.request.CheckInRequest;
import com.spring.jwt.Attendance.dto.request.CheckOutRequest;
import com.spring.jwt.Attendance.dto.request.LeaveRequestDTO;
import com.spring.jwt.Attendance.dto.response.AttendanceResponse;
import com.spring.jwt.Attendance.dto.response.LeaveRequestResponse;
import com.spring.jwt.Attendance.dto.response.MonthlyAttendanceResponse;
import com.spring.jwt.Attendance.service.AttendanceService;
import com.spring.jwt.Attendance.mapper.AttendanceMapper;
import com.spring.jwt.Attendance.model.Attendance;
import com.spring.jwt.Attendance.model.AttendanceStatus;
import com.spring.jwt.Attendance.model.LeaveRequest;
import com.spring.jwt.Attendance.model.embedded.Location;
import com.spring.jwt.Attendance.repository.AttendanceRepository;
import com.spring.jwt.Attendance.repository.LeaveRequestRepository;
import com.spring.jwt.entity.User;
import com.spring.jwt.exception.AttendanceException;
import com.spring.jwt.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Attendance Service Implementation
 * Implements business logic for attendance operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AttendanceServiceImpl implements AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final AttendanceMapper attendanceMapper;
    private final LeaveRequestRepository leaveRequestRepository;
    private final UserRepository userRepository;

    @Override
    public AttendanceResponse checkIn(CheckInRequest request) {
        log.info("Processing check-in for user: {} on date: {}", request.getUserId(), request.getDate());

        if (hasCheckedIn(request.getUserId(), request.getDate())) {
            throw new AttendanceException("You have already checked in for today");
        }

        Location checkInLocation = Location.builder()
            .latitude(request.getLocation().getLatitude())
            .longitude(request.getLocation().getLongitude())
            .address(request.getLocation().getAddress())
            .build();

        Attendance attendance = Attendance.builder()
            .userId(request.getUserId())
            .date(request.getDate())
            .attendanceStatus(request.getAttendanceStatus())
            .checkInTime(LocalTime.now())
            .checkInLocation(checkInLocation)
            .remarks(request.getRemarks())
            .createdBy(request.getUserId())
            .build();

        Attendance savedAttendance = attendanceRepository.save(attendance);
        log.info("Check-in successful for user: {} at {}", request.getUserId(), savedAttendance.getCheckInTime());

        return attendanceMapper.toResponse(savedAttendance);
    }

    @Override
    public AttendanceResponse checkOut(CheckOutRequest request) {
        log.info("Processing check-out for user: {} on date: {}", request.getUserId(), request.getDate());

        Attendance attendance = attendanceRepository.findByUserIdAndDate(request.getUserId(), request.getDate())
            .orElseThrow(() -> new AttendanceException("You must check in first before checking out"));

        if (!attendance.isCheckedIn()) {
            throw new AttendanceException("You must check in first before checking out");
        }

        if (attendance.isCheckedOut()) {
            throw new AttendanceException("You have already checked out for today");
        }

        Location checkOutLocation = Location.builder()
            .latitude(request.getLocation().getLatitude())
            .longitude(request.getLocation().getLongitude())
            .address(request.getLocation().getAddress())
            .build();

        attendance.setCheckOutTime(LocalTime.now());
        attendance.setCheckOutLocation(checkOutLocation);
        
        if (request.getRemarks() != null && !request.getRemarks().isEmpty()) {
            attendance.setRemarks(request.getRemarks());
        }
        
        attendance.setUpdatedBy(request.getUserId());
        attendance.calculateTotalHours();

        Attendance updatedAttendance = attendanceRepository.save(attendance);
        log.info("Check-out successful for user: {} at {}", request.getUserId(), updatedAttendance.getCheckOutTime());

        return attendanceMapper.toResponse(updatedAttendance);
    }

    @Override
    @Transactional(readOnly = true)
    public AttendanceResponse getTodayAttendance(Long userId) {
        log.debug("Fetching today's attendance for user: {}", userId);
        
        return attendanceRepository.findByUserIdAndDate(userId, LocalDate.now())
            .map(attendanceMapper::toResponse)
            .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public AttendanceResponse getAttendanceByDate(Long userId, LocalDate date) {
        log.debug("Fetching attendance for user: {} on date: {}", userId, date);
        
        return attendanceRepository.findByUserIdAndDate(userId, date)
            .map(attendanceMapper::toResponse)
            .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public MonthlyAttendanceResponse getMonthlyAttendance(Long userId, int year, int month) {
        log.info("Fetching monthly attendance for user: {} - {}/{}", userId, year, month);

        List<Attendance> attendances = attendanceRepository.findByUserIdAndYearAndMonth(userId, year, month);
        
        log.info("Found {} attendance records for user: {} in {}/{}", attendances.size(), userId, year, month);
        
        if (attendances.isEmpty()) {
            log.warn("No attendance records found for user: {} in {}/{}", userId, year, month);
            return null;
        }

        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        long presentCount = countByStatus(userId, startDate, endDate, AttendanceStatus.PRESENT);
        long absentCount = countByStatus(userId, startDate, endDate, AttendanceStatus.ABSENT);
        long leaveCount = countByStatus(userId, startDate, endDate, AttendanceStatus.LEAVE);
        long halfDayCount = countByStatus(userId, startDate, endDate, AttendanceStatus.HALF_DAY);
        long wfhCount = countByStatus(userId, startDate, endDate, AttendanceStatus.WORK_FROM_HOME);
        
        Double totalHours = attendanceRepository.getTotalWorkingHours(userId, startDate, endDate);
        double avgHours = attendances.isEmpty() ? 0 : totalHours / attendances.size();

        MonthlyAttendanceResponse.AttendanceSummary summary = MonthlyAttendanceResponse.AttendanceSummary.builder()
            .totalDays(attendances.size())
            .presentCount((int) presentCount)
            .absentCount((int) absentCount)
            .leaveCount((int) leaveCount)
            .halfDayCount((int) halfDayCount)
            .workFromHomeCount((int) wfhCount)
            .totalWorkingHours(totalHours)
            .averageWorkingHours(avgHours)
            .build();

        List<AttendanceResponse> records = attendances.stream()
            .map(attendanceMapper::toResponse)
            .collect(Collectors.toList());

        return MonthlyAttendanceResponse.builder()
            .records(records)
            .summary(summary)
            .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttendanceResponse> getAttendanceByDateRange(Long userId, LocalDate startDate, LocalDate endDate) {
        log.debug("Fetching attendance for user: {} from {} to {}", userId, startDate, endDate);
        
        List<Attendance> attendances = attendanceRepository.findByUserIdAndDateBetweenOrderByDateDesc(
            userId, startDate, endDate
        );
        
        return attendances.stream()
            .map(attendanceMapper::toResponse)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasCheckedIn(Long userId, LocalDate date) {
        return attendanceRepository.findByUserIdAndDate(userId, date)
            .map(Attendance::isCheckedIn)
            .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasCheckedOut(Long userId, LocalDate date) {
        return attendanceRepository.findByUserIdAndDate(userId, date)
            .map(Attendance::isCheckedOut)
            .orElse(false);
    }

    @Override
    public int autoCheckOut(LocalDate date) {
        log.info("Running auto check-out for date: {}", date);
        
        List<Attendance> pendingCheckouts = attendanceRepository.findPendingCheckouts(date);
        
        for (Attendance attendance : pendingCheckouts) {
            attendance.setCheckOutTime(LocalTime.of(18, 0)); // Default 6 PM
            attendance.setRemarks("Auto checked out by system");
            attendance.calculateTotalHours();
        }
        
        attendanceRepository.saveAll(pendingCheckouts);
        
        log.info("Auto check-out completed. {} records updated", pendingCheckouts.size());
        return pendingCheckouts.size();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MonthlyAttendanceResponse> getAllEmployeesMonthlyAttendance(int year, int month) {
        log.info("Fetching monthly attendance for all employees - {}/{}", year, month);

        List<Long> userIds = attendanceRepository.findDistinctUserIdsByYearAndMonth(year, month);
        
        log.info("Found {} employees with attendance records in {}/{}", userIds.size(), year, month);

        List<MonthlyAttendanceResponse> responses = new java.util.ArrayList<>();
        
        for (Long userId : userIds) {
            MonthlyAttendanceResponse response = getMonthlyAttendance(userId, year, month);
            if (response != null) {
                responses.add(response);
            }
        }
        
        return responses;
    }

    private long countByStatus(Long userId, LocalDate startDate, LocalDate endDate, AttendanceStatus status) {
        return attendanceRepository.countByUserIdAndDateRangeAndStatus(userId, startDate, endDate, status);
    }

    @Override
    public LeaveRequestResponse requestLeave(LeaveRequestDTO request) {
        log.info("Processing leave request for user: {} from {} to {}", 
            request.getUserId(), request.getStartDate(), request.getEndDate());

        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new AttendanceException("End date cannot be before start date");
        }

        if (leaveRequestRepository.hasOverlappingLeave(
                request.getUserId(), request.getStartDate(), request.getEndDate())) {
            throw new AttendanceException("You already have a leave request for overlapping dates");
        }

        User user = userRepository.findById(request.getUserId())
            .orElseThrow(() -> new AttendanceException("User not found"));

        String employeeName = user.getFirstName() + " " + user.getLastName();

        LeaveRequest leaveRequest = LeaveRequest.builder()
            .userId(request.getUserId())
            .employeeName(employeeName)
            .leaveType(request.getLeaveType())
            .startDate(request.getStartDate())
            .endDate(request.getEndDate())
            .reason(request.getReason())
            .status("PENDING")
            .build();

        LeaveRequest saved = leaveRequestRepository.save(leaveRequest);
        log.info("Leave request created with ID: {}", saved.getLeaveRequestId());

        return mapToLeaveResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LeaveRequestResponse> getAllLeaveRequests(Pageable pageable) {
        log.debug("Fetching all leave requests");
        
        Page<LeaveRequest> requests = leaveRequestRepository.findAll(pageable);
        return requests.map(this::mapToLeaveResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LeaveRequestResponse> getLeaveRequestsByStatus(String status, Pageable pageable) {
        log.debug("Fetching leave requests with status: {}", status);
        
        Page<LeaveRequest> requests = leaveRequestRepository.findByStatusOrderByRequestedAtDesc(
            status.toUpperCase(), pageable);
        return requests.map(this::mapToLeaveResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LeaveRequestResponse> getMyLeaveRequests(Long userId, Pageable pageable) {
        log.debug("Fetching leave requests for user: {}", userId);
        
        Page<LeaveRequest> requests = leaveRequestRepository.findByUserIdOrderByRequestedAtDesc(
            userId, pageable);
        return requests.map(this::mapToLeaveResponse);
    }

    @Override
    public LeaveRequestResponse approveLeaveRequest(Long leaveRequestId, Long approvedBy) {
        log.info("Approving leave request: {} by user: {}", leaveRequestId, approvedBy);

        LeaveRequest leaveRequest = leaveRequestRepository.findById(leaveRequestId)
            .orElseThrow(() -> new AttendanceException("Leave request not found"));

        if (!"PENDING".equals(leaveRequest.getStatus())) {
            throw new AttendanceException("Leave request has already been processed");
        }

        leaveRequest.setStatus("APPROVED");
        leaveRequest.setRespondedAt(LocalDateTime.now());
        leaveRequest.setRespondedBy(approvedBy);

        LeaveRequest updated = leaveRequestRepository.save(leaveRequest);
        log.info("Leave request approved: {}", leaveRequestId);

        return mapToLeaveResponse(updated);
    }

    @Override
    public LeaveRequestResponse rejectLeaveRequest(Long leaveRequestId, String rejectionReason, Long rejectedBy) {
        log.info("Rejecting leave request: {} by user: {}", leaveRequestId, rejectedBy);

        LeaveRequest leaveRequest = leaveRequestRepository.findById(leaveRequestId)
            .orElseThrow(() -> new AttendanceException("Leave request not found"));

        if (!"PENDING".equals(leaveRequest.getStatus())) {
            throw new AttendanceException("Leave request has already been processed");
        }

        leaveRequest.setStatus("REJECTED");
        leaveRequest.setRejectionReason(rejectionReason);
        leaveRequest.setRespondedAt(LocalDateTime.now());
        leaveRequest.setRespondedBy(rejectedBy);

        LeaveRequest updated = leaveRequestRepository.save(leaveRequest);
        log.info("Leave request rejected: {}", leaveRequestId);

        return mapToLeaveResponse(updated);
    }

    private LeaveRequestResponse mapToLeaveResponse(LeaveRequest request) {
        return LeaveRequestResponse.builder()
            .leaveRequestId(request.getLeaveRequestId())
            .userId(request.getUserId())
            .employeeName(request.getEmployeeName())
            .leaveType(request.getLeaveType())
            .startDate(request.getStartDate())
            .endDate(request.getEndDate())
            .totalDays(request.getTotalDays())
            .reason(request.getReason())
            .status(request.getStatus())
            .rejectionReason(request.getRejectionReason())
            .requestedAt(request.getRequestedAt())
            .respondedAt(request.getRespondedAt())
            .respondedBy(request.getRespondedBy())
            .build();
    }
}
