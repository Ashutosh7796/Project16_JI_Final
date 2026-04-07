package com.spring.jwt.exception;

public class UnauthorizedAccessException extends RuntimeException
{
    public UnauthorizedAccessException(String message) {
        super(message);
    }
}