package com.spring.jwt.Attendance.repository;

import com.spring.jwt.Attendance.model.Attendance;
import com.spring.jwt.Attendance.model.AttendanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Attendance Repository
 * Data access layer for attendance operations
 */
@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    /**
     * Find attendance by user ID and date
     */
    Optional<Attendance> findByUserIdAndDate(Long userId, LocalDate date);

    /**
     * Check if attendance exists for user on specific date
     */
    boolean existsByUserIdAndDate(Long userId, LocalDate date);

    /**
     * Find all attendance records for a user in a date range
     */
    List<Attendance> findByUserIdAndDateBetweenOrderByDateDesc(
        Long userId, 
        LocalDate startDate, 
        LocalDate endDate
    );

    /**
     * Find all attendance records for a user in a specific month
     */
    @Query("SELECT a FROM Attendance a WHERE a.userId = :userId " +
           "AND YEAR(a.date) = :year AND MONTH(a.date) = :month " +
           "ORDER BY a.date DESC")
    List<Attendance> findByUserIdAndYearAndMonth(
        @Param("userId") Long userId,
        @Param("year") int year,
        @Param("month") int month
    );

    /**
     * Find distinct user IDs who have attendance in a specific month
     */
    @Query("SELECT DISTINCT a.userId FROM Attendance a " +
           "WHERE YEAR(a.date) = :year AND MONTH(a.date) = :month")
    List<Long> findDistinctUserIdsByYearAndMonth(
        @Param("year") int year,
        @Param("month") int month
    );

    /**
     * Count attendance by status for a user in a date range
     */
    @Query("SELECT COUNT(a) FROM Attendance a WHERE a.userId = :userId " +
           "AND a.date BETWEEN :startDate AND :endDate " +
           "AND a.attendanceStatus = :status")
    long countByUserIdAndDateRangeAndStatus(
        @Param("userId") Long userId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        @Param("status") AttendanceStatus status
    );

    /**
     * Get total working hours for a user in a date range
     */
    @Query("SELECT COALESCE(SUM(a.totalHours), 0) FROM Attendance a " +
           "WHERE a.userId = :userId " +
           "AND a.date BETWEEN :startDate AND :endDate")
    Double getTotalWorkingHours(
        @Param("userId") Long userId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Find all attendance records for multiple users in a date range
     */
    List<Attendance> findByUserIdInAndDateBetweenOrderByDateDesc(
        List<Long> userIds,
        LocalDate startDate,
        LocalDate endDate
    );

    /**
     * Find attendance records without check-out for a specific date
     */
    @Query("SELECT a FROM Attendance a WHERE a.date = :date " +
           "AND a.checkInTime IS NOT NULL " +
           "AND a.checkOutTime IS NULL")
    List<Attendance> findPendingCheckouts(@Param("date") LocalDate date);

    /**
     * Delete old attendance records (for data retention policy)
     */
    void deleteByDateBefore(LocalDate date);
}
