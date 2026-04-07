package com.spring.jwt.utils;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenericResponseDto<T> {
    private String message;
    private T data;
    private String exception;
    
    public static <T> GenericResponseDto<T> success(String message, T data) {
        return new GenericResponseDto<>(message, data, null);
    }
    
    public static <T> GenericResponseDto<T> error(String message, String exception) {
        return new GenericResponseDto<>(message, null, exception);
    }
} 