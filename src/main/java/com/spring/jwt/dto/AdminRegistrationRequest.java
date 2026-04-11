package com.spring.jwt.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Admin registration request with additional security fields")
public class AdminRegistrationRequest {

    @Schema(description = "Admin's email address", example = "admin@company.com")
    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    @Size(max = 100, message = "Email must not exceed 100 characters")
    private String email;

    @Schema(description = "Admin's mobile number", example = "1234567890")
    @NotNull(message = "Mobile number is required")
    @Min(value = 1000000000L, message = "Mobile number must be at least 10 digits")
    @Max(value = 9999999999L, message = "Mobile number must not exceed 10 digits")
    private Long mobileNumber;

    @Schema(description = "Admin's password", example = "AdminSecurePass@123")
    @NotBlank(message = "Password is required")
    @Size(min = 12, max = 128, message = "Admin password must be between 12 and 128 characters")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{12,}$",
             message = "Admin password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character")
    private String password;

    @Schema(description = "Password confirmation", example = "AdminSecurePass@123")
    @NotBlank(message = "Password confirmation is required")
    private String confirmPassword;

    @Schema(description = "Admin's first name", example = "Admin")
    @NotBlank(message = "First name is required")
    @Size(min = 2, max = 50, message = "First name must be between 2 and 50 characters")
    private String firstName;

    @Schema(description = "Admin's last name", example = "User")
    @NotBlank(message = "Last name is required")
    @Size(min = 2, max = 50, message = "Last name must be between 2 and 50 characters")
    private String lastName;

    @Schema(description = "Admin registration secret key", example = "ADMIN_SECRET_KEY_2024")
    @NotBlank(message = "Admin secret key is required")
    private String adminSecretKey;

    @Schema(description = "Department or role description (optional)", example = "IT Administrator")
    @Size(max = 100, message = "Department must not exceed 100 characters")
    private String department;

}