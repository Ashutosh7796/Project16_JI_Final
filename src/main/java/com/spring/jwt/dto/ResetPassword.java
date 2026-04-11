package com.spring.jwt.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPassword {

    @NotBlank(message = "Token is required")
    @Schema(
            description = "Token Used to Reset Password", example = "FMfcgzQZTMQRvrFrnvXhzrdmXQzShWXq"
    )
    private String token;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters long")
    @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=])(?=\\S+$).{8,}$", 
             message = "Password must contain at least one digit, one lowercase, one uppercase letter, one special character, and no whitespace")
    @Schema(
            description = "Password Entered for Resetting ", example = "Pass@1234"
    )
    private String password;

    @NotBlank(message = "Confirm password is required")
    @Schema(
            description = "Match it with Password Entered for Resetting ", example = "Pass@1234"
    )
    private String confirmPassword;
}
