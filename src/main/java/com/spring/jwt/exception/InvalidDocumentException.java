package com.spring.jwt.exception;

public class InvalidDocumentException extends RuntimeException {
    public InvalidDocumentException(String message)
    {
        super(message);
    }
}
