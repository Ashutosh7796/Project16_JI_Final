package com.spring.jwt.EmployeeAttendance;

import com.spring.jwt.Enums.AttendanceStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class AttendanceAuditService {

    private final AttendanceAuditLogRepository auditRepository;

    public void logAttendanceChange(
            Long userId,
            LocalDate date,
            AttendanceStatus oldStatus,
            String oldReason,
            AttendanceStatus newStatus,
            String newReason,
            String actionType,
            Long actionByUserId
    ) {
        AttendanceAuditLog log = new AttendanceAuditLog();

        log.setUserId(userId);
        log.setAttendanceDate(date);
        log.setOldStatus(oldStatus);
        log.setOldReason(oldReason);
        log.setNewStatus(newStatus);
        log.setNewReason(newReason);
        log.setActionType(actionType);
        log.setActionByUserId(actionByUserId);

        auditRepository.save(log);
    }
}
