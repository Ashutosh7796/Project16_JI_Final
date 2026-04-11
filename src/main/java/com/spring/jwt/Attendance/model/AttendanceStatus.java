package com.spring.jwt.Attendance.model;

/**
 * Attendance Status Enum
 * Defines possible attendance statuses
 */
public enum AttendanceStatus {
    PRESENT("Present"),
    ABSENT("Absent"),
    LEAVE("Leave"),
    HALF_DAY("Half Day"),
    WORK_FROM_HOME("Work From Home");

    private final String displayName;

    AttendanceStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
