package com.spring.jwt.Employee;

import com.spring.jwt.EmployeeFarmerSurvey.BaseResponseDTO1;
import com.spring.jwt.dto.EmployeeByRoleResponse;
import com.spring.jwt.dto.PagedResponse;
import com.spring.jwt.exception.BaseException;
import com.spring.jwt.utils.ErrorResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    // ================= GET BY EMPLOYEE ID =================
    @GetMapping("/{employeeId}")
    public ResponseEntity<BaseResponseDTO1<EmployeeResponseDTO>> getEmployeeById(
            @PathVariable Long employeeId) {

        return ResponseEntity.ok(
                new BaseResponseDTO1<>(
                        "200",
                        "Employee fetched successfully",
                        employeeService.getEmployeeById(employeeId)
                )
        );
    }

    // ================= GET BY USER ID =================
    @GetMapping("/user/{userId}")
    public ResponseEntity<BaseResponseDTO1<EmployeeResponseDTO>> getEmployeeByUserId(
            @PathVariable Long userId) {

        return ResponseEntity.ok(
                new BaseResponseDTO1<>(
                        "200",
                        "Employee fetched successfully",
                        employeeService.getEmployeeByUserId(userId)
                )
        );
    }

    // ================= GET ALL (PAGINATION) =================
    @GetMapping("/all")
    public ResponseEntity<BaseResponseDTO1<Page<EmployeeResponseDTO>>> getAllEmployees(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        return ResponseEntity.ok(
                new BaseResponseDTO1<>(
                        "200",
                        "Employees fetched successfully",
                        employeeService.getAllEmployees(page, size)
                )
        );
    }

    @PatchMapping("/user/{userId}")
    public ResponseEntity<BaseResponseDTO1<EmployeeResponseDTO>> patchEmployeeByUserId(
            @PathVariable Long userId,
            @RequestBody EmployeeUpdateRequestDTO dto) {

        try {
            return ResponseEntity.ok(
                    new BaseResponseDTO1<>(
                            "200",
                            "Employee updated successfully",
                            employeeService.patchEmployeeByUserId(userId, dto)
                    )
            );
        } catch (BaseException e) {
            throw e;
        }
    }

    // ================= PATCH ACCOUNT LOCK BY USER ID =================
    @PatchMapping("/user/{userId}/account-lock")
    public ResponseEntity<BaseResponseDTO1<EmployeeResponseDTO>> updateAccountLockStatus(
            @PathVariable Long userId,
            @RequestParam Boolean accountLocked) {

        return ResponseEntity.ok(
                new BaseResponseDTO1<>(
                        "200",
                        "Account lock status updated successfully",
                        employeeService.updateAccountLockStatusByUserId(userId, accountLocked)
                )
        );
    }

    @GetMapping("/getUsers")
    public ResponseEntity<BaseResponseDTO1<Page<UserListResponseDTO>>> getUsers(
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<UserListResponseDTO> pageData = employeeService.getUsers(role, page, size);
        String message = StringUtils.hasText(role)
                ? "Accounts with role " + role + " retrieved successfully"
                : "Staff accounts retrieved successfully (administrators excluded)";
        return ResponseEntity.ok(new BaseResponseDTO1<>(
                "200",
                message,
                pageData
        ));
    }

    @Operation(
            summary = "Get all surveyors",
            description = "Returns a paginated list of all employees with the SURVEYOR role.",
            security = {@SecurityRequirement(name = "bearer-jwt")}
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Surveyors retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PagedResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @GetMapping("/getAll/surv")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<PagedResponse<EmployeeByRoleResponse>> getSurveyors(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(employeeService.getSurveyors(page, size));
    }

    @Operation(
            summary = "Get all lab technicians",
            description = "Returns a paginated list of all employees with the LAB_TECHNICIAN role.",
            security = {@SecurityRequirement(name = "bearer-jwt")}
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lab technicians retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PagedResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @GetMapping("/getAll/lab-tech")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PagedResponse<EmployeeByRoleResponse>> getLabTechnicians(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(employeeService.getLabTechnicians(page, size));
    }
}
