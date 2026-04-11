package com.spring.jwt.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ExamTimeWindowException extends RuntimeException {
    public ExamTimeWindowException(String message) {
        super(message);
    }
    
    public ExamTimeWindowException(String message, Throwable cause) {
        super(message, cause);
    }
}