package com.spring.jwt.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Employee details returned when querying by role")
public class EmployeeByRoleResponse {

    @Schema(description = "Unique user identifier", example = "10001")
    private Long userId;

    @Schema(description = "Employee code", example = "J_EMP-10001")
    private String employeeCode;

    @Schema(description = "First name", example = "John")
    private String firstName;

    @Schema(description = "Last name", example = "Doe")
    private String lastName;

    @Schema(description = "Email address", example = "john.doe@example.com")
    private String email;

    @Schema(description = "Mobile number", example = "9876543210")
    private Long mobileNumber;

}
