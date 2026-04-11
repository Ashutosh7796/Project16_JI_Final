package com.spring.jwt.utils.EmailVerificationService.EmailUtils;

import jakarta.persistence.Column;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VerifyOtpDTO {

    @NotBlank(message = "Email cannot be blank")
    @Email
    @Pattern(regexp = "^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$", message = "Invalid email format")
    @Column(name = "email", nullable = false, length = 250, unique = true)
    private String email;
    private String otp;

    public VerifyOtpDTO(String otp, String email) {
        this.otp = otp;
        this.email = email;
    }
}

