package com.spring.jwt.utils;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.http.HttpStatus;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@Schema(name = "ErrorResponse", description = "Schema to hold error response information")
public class ErrorResponseDto {

        @Schema(description = "API path invoked by client")
        private String apiPath;

        @Schema(description = "Error code representing the error happened")
        private HttpStatus errorCode;

        @Schema(description = "Error message representing the error happened")
        private String errorMessage;

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        @Schema(description = "Time representing when the error happened")
        private LocalDateTime errorTime;

}
