package com.spring.jwt.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResponseDto<T> {
    private String message;
    private T data;
    private String exception;

    public static <T> ResponseDto<T> success(String message, T data)
    {
        return new ResponseDto<>(message, data, null);
    }
    public static <T> ResponseDto<T> error(String message, String exception)
    {
        return new ResponseDto<>(message, null, exception);
    }
}