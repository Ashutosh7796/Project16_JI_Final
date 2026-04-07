package com.spring.jwt.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Secure user registration request with role & optional employee data")
public class SecureUserRegistrationRequest {

    @Schema(description = "User's email address", example = "user@example.com")
    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    @Size(max = 100, message = "Email must not exceed 100 characters")
    private String email;

    @Schema(description = "User's mobile number", example = "1234567890")
    @NotNull(message = "Mobile number is required")
    @Min(value = 1000000000L, message = "Mobile number must be 10 digits")
    @Max(value = 9999999999L, message = "Mobile number must be 10 digits")
    private Long mobileNumber;

    @Schema(description = "User's password", example = "SecurePass@123")
    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 128)
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).{8,}$",
            message = "Password must contain uppercase, lowercase, number & special character"
    )
    private String password;

    @Schema(description = "Password confirmation", example = "SecurePass@123")
    @NotBlank(message = "Confirm password is required")
    private String confirmPassword;

    @Schema(description = "User's first name", example = "John")
    @NotBlank
    @Size(min = 2, max = 50)
    @Pattern(regexp = "^[a-zA-Z\\s]+$", message = "First name can only contain letters")
    private String firstName;

    @Schema(description = "User's last name", example = "Doe")
    @NotBlank
    @Size(min = 2, max = 50)
    @Pattern(regexp = "^[a-zA-Z\\s]+$", message = "Last name can only contain letters")
    private String lastName;

    @Schema(
            description = "Role of the user (optional). Defaults to USER",
            example = "SURVEYOR",
            allowableValues = {"USER", "SURVEYOR", "LAB_TECHNICIAN"}
    )
    private String role;

    @Schema(description = "Employee code", example = "EMP-1001")
    private String employeeCode;

    private String companyName;
    private String address;
    private String permanentAddress;
    private String city;
    private String district;
    private String state;

    private String accountNumber;
    private String ifscCode;
    private String pfNumber;
    private String insuranceNumber;
    private String panNumber;
    private String vehicleNumber;
    private String description;

    @Schema(description = "Accept terms & conditions", example = "true")
    @NotNull
    @AssertTrue(message = "You must accept the terms and conditions")
    private Boolean acceptTerms;

    @Schema(description = "Accept privacy policy", example = "true")
    @NotNull
    @AssertTrue(message = "You must accept the privacy policy")
    private Boolean acceptPrivacyPolicy;

    @AssertTrue(message = "Password and confirm password must match")
    public boolean isPasswordMatching() {
        return password != null && password.equals(confirmPassword);
    }
}
