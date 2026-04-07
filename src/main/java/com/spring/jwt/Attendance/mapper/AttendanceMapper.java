package com.spring.jwt.Attendance.mapper;

import com.spring.jwt.Attendance.dto.response.AttendanceResponse;
import com.spring.jwt.Attendance.dto.response.LocationResponse;
import com.spring.jwt.Attendance.model.Attendance;
import com.spring.jwt.Attendance.model.embedded.Location;
import org.springframework.stereotype.Component;

import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Attendance Mapper
 * Maps between entity and DTO objects
 */
@Component("locationBasedAttendanceMapper")
public class AttendanceMapper {

    /**
     * Convert Attendance entity to AttendanceResponse DTO
     */
    public AttendanceResponse toResponse(Attendance attendance) {
        if (attendance == null) {
            return null;
        }

        return AttendanceResponse.builder()
            .attendanceId(attendance.getAttendanceId())
            .userId(attendance.getUserId())
            .date(attendance.getDate())
            .attendanceStatus(attendance.getAttendanceStatus())
            .checkInTime(attendance.getCheckInTime())
            .checkOutTime(attendance.getCheckOutTime())
            .checkInLocation(toLocationResponse(attendance.getCheckInLocation()))
            .checkOutLocation(toLocationResponse(attendance.getCheckOutLocation()))
            .remarks(attendance.getRemarks())
            .totalHours(attendance.getTotalHours())
            .day(attendance.getDate().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH))
            .checkedIn(attendance.isCheckedIn())
            .checkedOut(attendance.isCheckedOut())
            .build();
    }

    /**
     * Convert Location embedded object to LocationResponse DTO
     */
    public LocationResponse toLocationResponse(Location location) {
        if (location == null || !location.isValid()) {
            return null;
        }

        return LocationResponse.builder()
            .latitude(location.getLatitude())
            .longitude(location.getLongitude())
            .address(location.getAddress())
            .build();
    }
}
