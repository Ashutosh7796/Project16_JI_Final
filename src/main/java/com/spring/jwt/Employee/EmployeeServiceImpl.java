package com.spring.jwt.Employee;

import com.spring.jwt.dto.EmployeeByRoleResponse;
import com.spring.jwt.dto.PagedResponse;
import com.spring.jwt.entity.Employee;
import com.spring.jwt.entity.User;
import com.spring.jwt.exception.BaseException;
import com.spring.jwt.exception.ResourceNotFoundException;
import com.spring.jwt.repository.RoleRepository;
import com.spring.jwt.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Override
    public EmployeeResponseDTO getEmployeeById(Long employeeId) {

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Employee not found with ID: " + employeeId));

        return mapToDto(employee);
    }

    @Override
    public EmployeeResponseDTO getEmployeeByUserId(Long userId) {

        Employee employee = employeeRepository.findByUser_UserId(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Employee not found for User ID: " + userId));

        return mapToDto(employee);
    }

    @Override
    public Page<EmployeeResponseDTO> getAllEmployees(int page, int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("employeeId").descending());

        Page<Employee> employeePage = employeeRepository.findAll(pageable);

        if (employeePage.isEmpty()) {
            throw new ResourceNotFoundException("No employees found");
        }

        return employeePage.map(this::mapToResponse);
    }


    @Override
    @Transactional
    public EmployeeResponseDTO updateAccountLockStatus(Long employeeId, Boolean accountLocked) {

        try {
            if (accountLocked == null) {
                throw new BaseException("400", "accountLocked value must not be null");
            }

            Employee employee = employeeRepository.findById(employeeId)
                    .orElseThrow(() ->
                            new BaseException("404", "Employee not found with id: " + employeeId));

            User user = employee.getUser();

            // 🔐 Update only account lock status
            user.setAccountLocked(accountLocked);

            employeeRepository.save(employee); // cascades to User

            return mapToResponse(employee);

        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            throw new BaseException("500", "Failed to update account lock status");
        }
    }



    private EmployeeResponseDTO mapToDto(Employee employee) {

        User user = employee.getUser();

        EmployeeResponseDTO dto = new EmployeeResponseDTO();

        dto.setEmployeeId(employee.getEmployeeId());
        dto.setEmployeeCode(employee.getEmployeeCode());
        dto.setCompanyName(employee.getCompanyName());
        dto.setAddress(employee.getAddress());
        dto.setPermanentAddress(employee.getPermanentAddress());
        dto.setCity(employee.getCity());
        dto.setDistrict(employee.getDistrict());
        dto.setState(employee.getState());
        dto.setAccountNumber(employee.getAccountNumber());
        dto.setIfscCode(employee.getIfscCode());
        dto.setPfNumber(employee.getPfNumber());
        dto.setInsuranceNumber(employee.getInsuranceNumber());
        dto.setPanNumber(employee.getPanNumber());
        dto.setVehicleNumber(employee.getVehicleNumber());
        dto.setDescription(employee.getDescription());

        dto.setUserId(user.getUserId());
        dto.setUserEmail(user.getEmail());
        dto.setUserMobile(user.getMobileNumber());

        return dto;
    }
    @Override
    public EmployeeResponseDTO patchEmployeeByUserId(Long userId, EmployeeUpdateRequestDTO dto) {

        try {
            Employee employee = employeeRepository.findByUser_UserId(userId)
                    .orElseThrow(() ->
                            new BaseException("404", "Employee not found for userId: " + userId));

            User user = employee.getUser();

            // ---------- PATCH USER ----------
            if (dto.getEmail() != null && !dto.getEmail().isBlank()) {
                user.setEmail(dto.getEmail());
            }

            if (dto.getPhone() != null && !dto.getPhone().isBlank()) {
                try {
                    user.setMobileNumber(Long.parseLong(dto.getPhone()));
                } catch (NumberFormatException ex) {
                    throw new BaseException("400", "Invalid phone number format");
                }
            }

            // ---------- PATCH EMPLOYEE ----------
            if (dto.getCompanyName() != null)
                employee.setCompanyName(dto.getCompanyName());

            if (dto.getAddress() != null)
                employee.setAddress(dto.getAddress());

            if (dto.getPermanentAddress() != null)
                employee.setPermanentAddress(dto.getPermanentAddress());

            if (dto.getCity() != null)
                employee.setCity(dto.getCity());

            if (dto.getDistrict() != null)
                employee.setDistrict(dto.getDistrict());

            if (dto.getState() != null)
                employee.setState(dto.getState());

            if (dto.getAccountNumber() != null)
                employee.setAccountNumber(dto.getAccountNumber());

            if (dto.getIfscCode() != null)
                employee.setIfscCode(dto.getIfscCode());

            if (dto.getPfNumber() != null)
                employee.setPfNumber(dto.getPfNumber());

            if (dto.getInsuranceNumber() != null)
                employee.setInsuranceNumber(dto.getInsuranceNumber());

            if (dto.getPanNumber() != null)
                employee.setPanNumber(dto.getPanNumber());

            if (dto.getVehicleNumber() != null)
                employee.setVehicleNumber(dto.getVehicleNumber());

            if (dto.getDescription() != null)
                employee.setDescription(dto.getDescription());

            employeeRepository.save(employee);

            return mapToResponse(employee);

        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            throw new BaseException("500", "Failed to update employee details");
        }
    }

    @Override
    @Transactional
    public EmployeeResponseDTO updateAccountLockStatusByUserId(Long userId, Boolean accountLocked) {

        if (accountLocked == null) {
            throw new BaseException("400", "accountLocked value must not be null");
        }

        Employee employee = employeeRepository.findByUser_UserId(userId)
                .orElseThrow(() ->
                        new BaseException("404", "Employee not found for userId: " + userId));

        employee.getUser().setAccountLocked(accountLocked);

        employeeRepository.save(employee);

        return mapToResponse(employee);
    }


    @Override
    public Page<UserListResponseDTO> getUsers(String role, int page, int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("userId").descending());

        Page<User> usersPage;

        if (StringUtils.hasText(role)) {
            String finalRole = role.toUpperCase();
            if ("ADMIN".equals(finalRole)) {
                // Administrators are never listed via this endpoint
                usersPage = Page.empty(pageable);
            } else {
                usersPage = userRepository.findAllByRoleNameExcludingAdminHolders(finalRole, pageable);
            }
        } else {
            usersPage = userRepository.findAllEmployees(pageable);
        }

        return usersPage.map(user ->
                new UserListResponseDTO(
                        user.getUserId(),
                        buildFullName(user),
                        user.getEmail(),
                        user.getMobileNumber(),
                        user.getAccountLocked(),
                        buildEmployeeCode(user)
                )
        );
    }
    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public PagedResponse<EmployeeByRoleResponse> getSurveyors(int page, int size) {
        return fetchByRole("SURVEYOR", page, size);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public PagedResponse<EmployeeByRoleResponse> getLabTechnicians(int page, int size) {
        return fetchByRole("LAB_TECHNICIAN", page, size);
    }

    private PagedResponse<EmployeeByRoleResponse> fetchByRole(String role, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<User> userPage = userRepository.findAllByRoleNameExcludingAdminHolders(role, pageable);

        List<EmployeeByRoleResponse> content = userPage.getContent()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return PagedResponse.<EmployeeByRoleResponse>builder()
                .content(content)
                .page(userPage.getNumber())
                .size(userPage.getSize())
                .totalElements(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .last(userPage.isLast())
                .build();
    }

    private String buildFullName(User user) {
        return (user.getFirstName() == null ? "" : user.getFirstName()) +
                " " +
                (user.getLastName() == null ? "" : user.getLastName());
    }
    private String buildEmployeeCode(User user) {
        return ("EMP"+user.getUserId());
    }

    private EmployeeResponseDTO mapToResponse(Employee employee) {

        EmployeeResponseDTO dto = new EmployeeResponseDTO();
        User user = employee.getUser();

        // ---------- IDS ----------
        dto.setEmployeeId(employee.getEmployeeId());
        dto.setUserId(user.getUserId());

        // ---------- USER ----------


        // ---------- EMPLOYEE ----------
        dto.setEmployeeCode(employee.getEmployeeCode());
        dto.setCompanyName(employee.getCompanyName());
        dto.setAddress(employee.getAddress());
        dto.setPermanentAddress(employee.getPermanentAddress());
        dto.setCity(employee.getCity());
        dto.setDistrict(employee.getDistrict());
        dto.setState(employee.getState());
        dto.setAccountNumber(employee.getAccountNumber());
        dto.setIfscCode(employee.getIfscCode());
        dto.setPfNumber(employee.getPfNumber());
        dto.setInsuranceNumber(employee.getInsuranceNumber());
        dto.setPanNumber(employee.getPanNumber());
        dto.setVehicleNumber(employee.getVehicleNumber());
        dto.setDescription(employee.getDescription());

        return dto;
    }

    private EmployeeByRoleResponse toResponse(User user) {
        Employee emp = user.getEmployee();

        return EmployeeByRoleResponse.builder()
                .userId(user.getUserId())
                .employeeCode(emp != null ? emp.getEmployeeCode() : null)
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .mobileNumber(user.getMobileNumber())
                .build();
    }

}

