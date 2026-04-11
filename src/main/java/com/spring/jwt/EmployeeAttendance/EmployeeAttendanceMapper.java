package com.spring.jwt.EmployeeAttendance;

import com.spring.jwt.Enums.AttendanceStatus;
import com.spring.jwt.entity.EmployeeAttendance;
import com.spring.jwt.entity.User;
import org.springframework.stereotype.Component;

@Component("employeeAttendanceMapper")
public class EmployeeAttendanceMapper {

    public EmployeeAttendanceDTO toDto(EmployeeAttendance entity) {

        EmployeeAttendanceDTO dto = new EmployeeAttendanceDTO();

        dto.setAttendanceId(entity.getAttendanceId());
        dto.setEmployeeCode(entity.getEmployeeCode());
        dto.setEmployeeName(entity.getEmployeeName());
        dto.setDate(entity.getDate());
        dto.setAttendanceStatus(entity.getAttendanceStatus());
        dto.setReason(entity.getReason());
        dto.setAttendanceStatusApproval(
                entity.getAttendanceStatusApproval()
        );
        dto.setUserId(entity.getUser().getUserId());

        return dto;
    }

    public EmployeeAttendance toEntity(
            EmployeeAttendanceDTO dto,
            User user
    ) {
        EmployeeAttendance entity = new EmployeeAttendance();
        entity.setEmployeeCode(dto.getEmployeeCode());
        entity.setEmployeeName(dto.getEmployeeName());
        entity.setDate(dto.getDate());
        entity.setAttendanceStatus(dto.getAttendanceStatus());
        entity.setReason(dto.getReason());
        entity.setUser(user);

        entity.setAttendanceStatusApproval(
                dto.getAttendanceStatus() == AttendanceStatus.PRESENT
        );

        return entity;
    }
}
