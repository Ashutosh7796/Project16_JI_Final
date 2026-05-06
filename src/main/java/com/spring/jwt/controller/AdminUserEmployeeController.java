package com.spring.jwt.controller;

import com.spring.jwt.Employee.EmployeeService;
import com.spring.jwt.dto.AdminResetEmployeePasswordRequest;
import com.spring.jwt.utils.BaseResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin user routes aligned with {@code /api/v1/admin/users/employees/**} security rules.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserEmployeeController {

    private final EmployeeService employeeService;

    /**
     * Set a new password for a staff account (surveyor / lab technician / manager)
     * when the employee forgot their password. Administrator accounts cannot be reset here.
     */
    @PostMapping("/employees/{userId}/reset-password")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<BaseResponseDTO> resetEmployeePassword(
            @PathVariable Long userId,
            @Valid @RequestBody AdminResetEmployeePasswordRequest request) {

        employeeService.adminResetStaffPassword(userId, request.getNewPassword());
        return ResponseEntity.ok(new BaseResponseDTO("200", "Password updated successfully", null));
    }
}
