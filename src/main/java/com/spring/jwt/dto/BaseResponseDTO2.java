package com.spring.jwt.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BaseResponseDTO2<T> {

    private int status;
    private String message;
    private T data;

    public static <T> BaseResponseDTO2<T> success(
            HttpStatus status, String message, T data) {
        return new BaseResponseDTO2<>(
                status.value(),
                message,
                data
        );
    }

    public static <T> BaseResponseDTO2<T> error(
            HttpStatus status, String message) {
        return new BaseResponseDTO2<>(
                status.value(),
                message,
                null
        );
    }
}
