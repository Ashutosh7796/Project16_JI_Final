package com.spring.jwt.Employee;


import com.spring.jwt.dto.EmployeeByRoleResponse;
import com.spring.jwt.dto.PagedResponse;
import com.spring.jwt.entity.User;
import org.springframework.data.domain.Page;

import java.util.List;

public interface EmployeeService {

    EmployeeResponseDTO getEmployeeById(Long employeeId);

    EmployeeResponseDTO getEmployeeByUserId(Long userId);

    public Page<EmployeeResponseDTO> getAllEmployees(int page, int size);

    // Status-only update
    EmployeeResponseDTO updateAccountLockStatus(Long employeeId, Boolean accountLocked);

    EmployeeResponseDTO patchEmployeeByUserId(Long userId, EmployeeUpdateRequestDTO dto);

    EmployeeResponseDTO updateAccountLockStatusByUserId(Long userId, Boolean accountLocked);

    Page<UserListResponseDTO> getUsers(String role, int page, int size);

    PagedResponse<EmployeeByRoleResponse> getSurveyors(int page, int size);

    PagedResponse<EmployeeByRoleResponse> getLabTechnicians(int page, int size);
}
