package com.spring.jwt.exception;

public class DocumentNotFoundException extends RuntimeException
{
    public DocumentNotFoundException(String message)
    {
        super(message);
    }
}
