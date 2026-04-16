package com.spring.jwt.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
@EqualsAndHashCode(callSuper = false)
public class BaseException extends RuntimeException {

    private String code;

    /** Business / API message (also passed to {@link RuntimeException#RuntimeException(String)} for correct {@link #getMessage()}). */
    private String message;

    public BaseException(String code, String message) {
        super(message != null ? message : "");
        this.code = code;
        this.message = message;
    }

    public BaseException(String code, String message, Throwable cause) {
        super(message != null ? message : "", cause);
        this.code = code;
        this.message = message;
    }

    /** When logs need the human text regardless of Throwable edge cases. */
    public String getOutcomeDescription() {
        return message != null ? message : "";
    }
}
