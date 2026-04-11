package com.spring.jwt.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserUpdateRequest {
    @Email(message = "Invalid email format")
    @Pattern(regexp = "^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$", message = "Invalid email format")
    @Schema(
            description = "Email of User", example = "example@example.com"
    )
    private String email;

    @Size(max = 255, message = "Address cannot exceed 255 characters")
    @Schema(
            description = "Address of the customer", example = "A/P Pune Main Street Block no 8"
    )
    private String address;

    @Size(min = 2, max = 50, message = "First name must be between 2 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z\\s'-]+$", message = "First name can contain letters, spaces, hyphens and apostrophes")
    @Schema(
            description = "First Name of the customer", example = "John"
    )
    private String firstName;
    
    @Size(min = 2, max = 50, message = "Last name must be between 2 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z\\s'-]+$", message = "Last name can contain letters, spaces, hyphens and apostrophes")
    @Schema(
            description = "Last Name of the customer", example = "Doe"
    )
    private String lastName;

    @Pattern(regexp = "^[0-9]{10}$", message = "Mobile number must be 10 digits")
    @Schema(
            description = "Mobile Number of the customer", example = "9822222212"
    )
    private Long mobileNumber;
}

