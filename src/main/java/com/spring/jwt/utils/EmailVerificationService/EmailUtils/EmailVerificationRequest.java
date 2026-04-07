package com.spring.jwt.utils.EmailVerificationService.EmailUtils;

import lombok.Data;

@Data
public class EmailVerificationRequest {
    private String email;
    private String otp;
}