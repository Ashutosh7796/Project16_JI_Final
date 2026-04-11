package com.spring.jwt.EmployeeAttendance;


import com.spring.jwt.Employee.EmployeeRepository;
import com.spring.jwt.EmployeeAttendance.Holiday.WeeklyOffUtil;
import com.spring.jwt.EmployeeAttendance.Holiday.WorkingDayUtil;
import com.spring.jwt.Enums.AttendanceStatus;
import com.spring.jwt.entity.Employee;
import com.spring.jwt.entity.EmployeeAttendance;
import com.spring.jwt.entity.User;
import com.spring.jwt.exception.ResourceNotFoundException;
import com.spring.jwt.exception.UserNotFoundExceptions;
import com.spring.jwt.repository.UserRepository;
import com.spring.jwt.utils.SecurityUtil;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EmployeeAttendanceServiceImpl
        implements EmployeeAttendanceService {

    private final EmployeeAttendanceRepository attendanceRepository;
    private final UserRepository userRepository;
    private final EmployeeAttendanceMapper attendanceMapper;
    private final SecurityUtil securityUtil;
    private final WorkingDayUtil workingDayUtil;
    private final WeeklyOffUtil weeklyOffUtil;
    private final EmployeeRepository employeeRepository;
    private final AttendanceAuditService auditService;


    @Override
    public EmployeeAttendanceDTO markAttendance(EmployeeAttendanceDTO dto) {

        Long userId = securityUtil.getCurrentUserId();

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new UserNotFoundExceptions(
                                "User not found with ID: " + userId));


        String fullName = user.getFirstName() + " " + user.getLastName();

        Employee employee = employeeRepository.findByUser_UserId(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Employee record not found"));

        LocalDate attendanceDate;

        // ================= PRESENT CASE =================
        if (dto.getAttendanceStatus() == AttendanceStatus.PRESENT) {

            // 🔒 Force server date
            attendanceDate = LocalDate.now();

        } else {
            // ================= ABSENT / LEAVE CASE =================
            if (dto.getDate() == null) {
                throw new IllegalArgumentException(
                        "Date is required for ABSENT or LEAVE");
            }

            if (dto.getReason() == null || dto.getReason().isBlank()) {
                throw new IllegalArgumentException(
                        "Reason is required for ABSENT or LEAVE");
            }

            attendanceDate = dto.getDate();
        }

        // ================= DUPLICATE CHECK =================
        boolean alreadyMarked =
                attendanceRepository.existsByUser_UserIdAndDate(
                        userId, attendanceDate);

        if (alreadyMarked) {
            throw new IllegalArgumentException(
                    "Attendance already marked for date: "
                            + attendanceDate);
        }

        // ================= CREATE ENTITY =================
        EmployeeAttendance entity = new EmployeeAttendance();

        entity.setUser(user);
        entity.setEmployeeCode(employee.getEmployeeCode());
        entity.setEmployeeName(fullName);
        entity.setDate(attendanceDate);
        entity.setAttendanceStatus(dto.getAttendanceStatus());
        entity.setReason(dto.getReason());

        // Auto approval for PRESENT only
        entity.setAttendanceStatusApproval(
                dto.getAttendanceStatus() == AttendanceStatus.PRESENT
        );

        return attendanceMapper.toDto(
                attendanceRepository.save(entity));
    }


    @Override
    public EmployeeAttendanceDTO getAttendanceById(Long attendanceId) {

        EmployeeAttendance attendance =
                attendanceRepository.findById(attendanceId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Attendance not found with ID: "
                                                + attendanceId));

        return attendanceMapper.toDto(attendance);
    }


    @Override
    public Page<EmployeeAttendanceDTO> getAllAttendance(Pageable pageable) {

        Page<EmployeeAttendance> page =
                attendanceRepository.findAll(pageable);

        if (page.isEmpty()) {
            throw new ResourceNotFoundException(
                    "No attendance records found");
        }

        return page.map(attendanceMapper::toDto);
    }

    @Override
    public Page<EmployeeAttendanceDTO> getMyAttendance(Pageable pageable) {

        Long userId = securityUtil.getCurrentUserId();

        Page<EmployeeAttendance> page =
                attendanceRepository.findByUser_UserId(
                        userId, pageable);

        if (page.isEmpty()) {
            throw new ResourceNotFoundException(
                    "No attendance records found for user");
        }

        return page.map(attendanceMapper::toDto);
    }


    @Override
    public Page<EmployeeAttendanceDTO> getAttendanceByStatus(
            AttendanceStatus status, Pageable pageable) {

        Page<EmployeeAttendance> page =
                attendanceRepository.findByAttendanceStatus(
                        status, pageable);

        if (page.isEmpty()) {
            throw new ResourceNotFoundException(
                    "No attendance found with status: " + status);
        }

        return page.map(attendanceMapper::toDto);
    }


    @Override
    public Page<EmployeeAttendanceDTO> getMyAttendanceByStatus(
            AttendanceStatus status, Pageable pageable) {

        Long userId = securityUtil.getCurrentUserId();

        Page<EmployeeAttendance> page =
                attendanceRepository
                        .findByAttendanceStatusAndUser_UserId(
                                status, userId, pageable);

        if (page.isEmpty()) {
            throw new ResourceNotFoundException(
                    "No attendance found for user with status: "
                            + status);
        }

        return page.map(attendanceMapper::toDto);
    }


    @Override
    public EmployeeAttendanceDTO approveAttendance(Long attendanceId) {

        EmployeeAttendance attendance =
                attendanceRepository.findById(attendanceId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Attendance not found with ID: " + attendanceId));

        attendance.setAttendanceStatusApproval(true);

        EmployeeAttendance saved =
                attendanceRepository.save(attendance);


        auditService.logAttendanceChange(
                attendance.getUser().getUserId(),
                attendance.getDate(),
                attendance.getAttendanceStatus(),
                attendance.getReason(),
                attendance.getAttendanceStatus(),
                attendance.getReason(),
                "APPROVED",
                securityUtil.getCurrentUserId()
        );

        return attendanceMapper.toDto(saved);
    }


    @Override
    public EmployeeAttendanceDTO rejectAttendance(
            Long attendanceId, String reason) {

        EmployeeAttendance attendance =
                attendanceRepository.findById(attendanceId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Attendance not found with ID: " + attendanceId));

        AttendanceStatus oldStatus =
                attendance.getAttendanceStatus();
        String oldReason =
                attendance.getReason();

        attendance.setAttendanceStatusApproval(false);
        attendance.setReason(reason);

        EmployeeAttendance saved =
                attendanceRepository.save(attendance);


        auditService.logAttendanceChange(
                attendance.getUser().getUserId(),
                attendance.getDate(),
                oldStatus,
                oldReason,
                oldStatus,
                reason,
                "REJECTED",
                securityUtil.getCurrentUserId()
        );

        return attendanceMapper.toDto(saved);
    }


    @Override
    public AttendanceDashboardDTO getAttendanceDashboardCount() {

        Long userId = securityUtil.getCurrentUserId();

        long present =
                attendanceRepository
                        .countByAttendanceStatusAndUser_UserId(
                                AttendanceStatus.PRESENT, userId);

        long absent =
                attendanceRepository
                        .countByAttendanceStatusAndUser_UserId(
                                AttendanceStatus.ABSENT, userId);

        long leave =
                attendanceRepository
                        .countByAttendanceStatusAndUser_UserId(
                                AttendanceStatus.LEAVE, userId);

        return new AttendanceDashboardDTO(
                present, absent, leave);
    }


    @Override
    public MonthlyAttendanceSummaryDTO
    getMyMonthlyAttendanceSummary(int month, int year) {

        Long userId = securityUtil.getCurrentUserId();

        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end =
                start.withDayOfMonth(start.lengthOfMonth());

        long present =
                attendanceRepository
                        .countByUser_UserIdAndAttendanceStatusAndDateBetween(
                                userId,
                                AttendanceStatus.PRESENT,
                                start,
                                end);

        long absent =
                attendanceRepository
                        .countByUser_UserIdAndAttendanceStatusAndDateBetween(
                                userId,
                                AttendanceStatus.ABSENT,
                                start,
                                end);

        long leave =
                attendanceRepository
                        .countByUser_UserIdAndAttendanceStatusAndDateBetween(
                                userId,
                                AttendanceStatus.LEAVE,
                                start,
                                end);

        long total =
                attendanceRepository
                        .countByUser_UserIdAndDateBetween(
                                userId, start, end);

        return new MonthlyAttendanceSummaryDTO(
                month,
                year,
                total,
                present,
                absent,
                leave
        );
    }


    @Override
    public EmployeeMonthlyAttendanceReportDTO
    getMyMonthlyAttendanceFullReport(int month, int year) {

        Long userId = securityUtil.getCurrentUserId();

        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end =
                start.withDayOfMonth(start.lengthOfMonth());

        List<EmployeeAttendance> records =
                attendanceRepository
                        .findByUser_UserIdAndDateBetween(
                                userId, start, end);

        if (records.isEmpty()) {
            throw new ResourceNotFoundException(
                    "No attendance found for this month");
        }

        long present =
                records.stream()
                        .filter(a ->
                                a.getAttendanceStatus()
                                        == AttendanceStatus.PRESENT)
                        .count();

        long absent =
                records.stream()
                        .filter(a ->
                                a.getAttendanceStatus()
                                        == AttendanceStatus.ABSENT)
                        .count();

        long leave =
                records.stream()
                        .filter(a ->
                                a.getAttendanceStatus()
                                        == AttendanceStatus.LEAVE)
                        .count();

        List<AttendanceDayReportDTO> dailyReport =
                records.stream()
                        .map(a -> new AttendanceDayReportDTO(
                                a.getDate(),
                                a.getAttendanceStatus(),
                                a.getAttendanceStatusApproval(),
                                a.getReason()
                        ))
                        .sorted(Comparator.comparing(
                                AttendanceDayReportDTO::getDate))
                        .toList();

        EmployeeAttendance first = records.get(0);

        return new EmployeeMonthlyAttendanceReportDTO(
                first.getEmployeeCode(),
                first.getEmployeeName(),
                month,
                year,
                present,
                absent,
                leave,
                dailyReport
        );
    }


    @Override
    public void deleteAttendance(Long attendanceId) {

        if (!attendanceRepository.existsById(attendanceId)) {
            throw new ResourceNotFoundException(
                    "Attendance not found with ID: " + attendanceId);
        }
        attendanceRepository.deleteById(attendanceId);
    }

    @Transactional
    public void autoMarkAbsentForDate(LocalDate date) {

        // ✅ Skip weekly off & holidays
        if (!weeklyOffUtil.isWorkingDay(date)) {
            return;
        }

        List<User> users =
                userRepository.findAllByStatusTrue();

        for (User user : users) {

            boolean exists =
                    attendanceRepository
                            .existsByUser_UserIdAndDate(
                                    user.getUserId(), date);

            if (!exists) {

                // 🔹 Get employee for empNumber
                Employee employee =
                        employeeRepository
                                .findByUser_UserId(user.getUserId())
                                .orElseThrow(() ->
                                        new ResourceNotFoundException(
                                                "Employee not found for userId: "
                                                        + user.getUserId()));

                String fullName =
                        user.getFirstName() + " " + user.getLastName();

                EmployeeAttendance absent =
                        new EmployeeAttendance();

                absent.setUser(user);
                absent.setEmployeeCode(employee.getEmployeeCode());
                absent.setEmployeeName(fullName);
                absent.setDate(date);
                absent.setAttendanceStatus(
                        AttendanceStatus.ABSENT);
                absent.setAttendanceStatusApproval(false);
                absent.setReason("Auto marked absent");

                attendanceRepository.save(absent);
                auditService.logAttendanceChange(
                        user.getUserId(),
                        date,
                        null,
                        null,
                        AttendanceStatus.ABSENT,
                        "Auto marked absent",
                        "AUTO_ABSENT",
                        0L   // system
                );
            }
        }
    }


    @Transactional
    public EmployeeAttendanceDTO adminOverrideAttendance(
            AdminAttendanceOverrideDTO dto) {

        EmployeeAttendance attendance =
                attendanceRepository
                        .findByUser_UserIdAndDate(
                                dto.getUserId(), dto.getDate())
                        .orElse(null);

        AttendanceStatus oldStatus = null;
        String oldReason = null;

        if (attendance == null) {
            // Create new attendance record
            User user = userRepository.findById(dto.getUserId())
                    .orElseThrow(() ->
                            new UserNotFoundExceptions(
                                    "User not found with ID: " + dto.getUserId()));

            Employee employee = employeeRepository.findByUser_UserId(dto.getUserId())
                    .orElseThrow(() ->
                            new ResourceNotFoundException(
                                    "Employee record not found"));

            String fullName = user.getFirstName() + " " + user.getLastName();

            attendance = new EmployeeAttendance();
            attendance.setUser(user);
            attendance.setEmployeeCode(employee.getEmployeeCode());
            attendance.setEmployeeName(fullName);
            attendance.setDate(dto.getDate());
        } else {
            oldStatus = attendance.getAttendanceStatus();
            oldReason = attendance.getReason();
        }

        attendance.setAttendanceStatus(dto.getStatus());
        attendance.setReason(dto.getReason());
        attendance.setAttendanceStatusApproval(true);

        EmployeeAttendance saved =
                attendanceRepository.save(attendance);

        // ✅ ADD THIS (AUDIT LOG)
        auditService.logAttendanceChange(
                dto.getUserId(),
                dto.getDate(),
                oldStatus,
                oldReason,
                dto.getStatus(),
                dto.getReason(),
                "ADMIN_OVERRIDE",
                securityUtil.getCurrentUserId()
        );

        return attendanceMapper.toDto(saved);
    }



}

