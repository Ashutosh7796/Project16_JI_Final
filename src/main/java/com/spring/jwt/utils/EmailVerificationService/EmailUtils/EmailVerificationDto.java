package com.spring.jwt.utils.EmailVerificationService.EmailUtils;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class EmailVerificationDto {


    private Integer id;

    @NotBlank(message = "Email cannot be blank")
    @Email(message = "Invalid email format")
    private String email;

    private String status;
    private String salt;
    private String hashedOtp;
    private LocalDateTime creationTime;
    private LocalDateTime expiryTime;
    private int attempts;
    private String ipAddress;
}
