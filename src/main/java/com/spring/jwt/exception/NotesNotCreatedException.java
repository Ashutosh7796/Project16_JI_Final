package com.spring.jwt.exception;

public class NotesNotCreatedException extends RuntimeException {
    public NotesNotCreatedException(String message) {
        super(message);
    }
}