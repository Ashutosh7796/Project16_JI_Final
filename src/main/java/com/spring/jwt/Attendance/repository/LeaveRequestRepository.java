package com.spring.jwt.Attendance.repository;

import com.spring.jwt.Attendance.model.LeaveRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Leave Request Repository
 */
@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    /**
     * Find all leave requests for a user
     */
    Page<LeaveRequest> findByUserIdOrderByRequestedAtDesc(Long userId, Pageable pageable);

    /**
     * Find leave requests by status
     */
    Page<LeaveRequest> findByStatusOrderByRequestedAtDesc(String status, Pageable pageable);

    /**
     * Find leave requests by user and status
     */
    Page<LeaveRequest> findByUserIdAndStatusOrderByRequestedAtDesc(
        Long userId, 
        String status, 
        Pageable pageable
    );

    /**
     * Check if user has overlapping leave requests
     */
    @Query("SELECT COUNT(lr) > 0 FROM LeaveRequest lr WHERE lr.userId = :userId " +
           "AND lr.status IN ('PENDING', 'APPROVED') " +
           "AND ((lr.startDate <= :endDate AND lr.endDate >= :startDate))")
    boolean hasOverlappingLeave(
        @Param("userId") Long userId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Get approved leaves for a user in a date range
     */
    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.userId = :userId " +
           "AND lr.status = 'APPROVED' " +
           "AND lr.startDate <= :endDate AND lr.endDate >= :startDate")
    List<LeaveRequest> findApprovedLeavesInRange(
        @Param("userId") Long userId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
}
