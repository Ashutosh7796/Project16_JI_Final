package com.spring.jwt.exception;

/**
 * Attendance Exception
 * Custom exception for attendance-related errors
 */
public class AttendanceException extends RuntimeException {

    public AttendanceException(String message) {
        super(message);
    }

    public AttendanceException(String message, Throwable cause) {
        super(message, cause);
    }
}
