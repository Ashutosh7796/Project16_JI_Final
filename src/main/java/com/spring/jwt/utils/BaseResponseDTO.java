package com.spring.jwt.utils;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.poi.ss.formula.functions.T;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BaseResponseDTO {

    private String code;

    private String message;
    private Long userID;

//    private String message;
//    private boolean success;
//    private Object data;
//    private HttpStatus status;
//    private HttpStatusCode statusCode;
//    private String errorCode;
//    private String errorDetails;
//    private String code;
//
//    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
//    @Builder.Default
//    @Schema(description = "Timestamp of the response")
//    private LocalDateTime timestamp = LocalDateTime.now();
//
//    public BaseResponseDTO(String message, boolean success, HttpStatus status) {
//        this.message = message;
//        this.success = success;
//        this.status = status;
//
//    }
}
