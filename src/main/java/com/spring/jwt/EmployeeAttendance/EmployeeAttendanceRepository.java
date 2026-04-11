package com.spring.jwt.EmployeeAttendance;

import com.spring.jwt.Enums.AttendanceStatus;
import com.spring.jwt.entity.EmployeeAttendance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmployeeAttendanceRepository extends JpaRepository<EmployeeAttendance, Long> {

    Page<EmployeeAttendance> findByUser_UserId(
            Long userId,
            Pageable pageable
    );

    Page<EmployeeAttendance> findByAttendanceStatus(
            AttendanceStatus status,
            Pageable pageable
    );

    Page<EmployeeAttendance> findByAttendanceStatusAndUser_UserId(
            AttendanceStatus status,
            Long userId,
            Pageable pageable
    );

    long countByAttendanceStatus(
            AttendanceStatus status
    );

    long countByAttendanceStatusAndUser_UserId(
            AttendanceStatus status,
            Long userId
    );

    long countByUser_UserIdAndAttendanceStatusAndDateBetween(
            Long userId,
            AttendanceStatus status,
            LocalDate startDate,
            LocalDate endDate
    );

    long countByUser_UserIdAndDateBetween(
            Long userId,
            LocalDate startDate,
            LocalDate endDate
    );

    List<EmployeeAttendance> findByUser_UserIdAndDateBetween(
            Long userId,
            LocalDate startDate,
            LocalDate endDate
    );

    boolean existsByUser_UserIdAndDate(
            Long userId,
            LocalDate date
    );

    Optional<EmployeeAttendance> findByUser_UserIdAndDate(
            Long userId,
            LocalDate date
    );
}
