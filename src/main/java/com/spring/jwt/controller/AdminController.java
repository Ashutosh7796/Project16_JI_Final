package com.spring.jwt.controller;

import com.spring.jwt.dto.AdminDashboardStatsDTO;
import com.spring.jwt.dto.UserDTO;
import com.spring.jwt.entity.Role;
import com.spring.jwt.entity.User;
import com.spring.jwt.repository.RoleRepository;
import com.spring.jwt.repository.UserRepository;
import com.spring.jwt.service.AdminDashboardService;
import com.spring.jwt.utils.ApiResponse;
import com.spring.jwt.utils.BaseResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminDashboardService adminDashboardService;

    /**
     * Dashboard KPIs + 8-week order track. Allowed for admin and manager roles.
     */
    @GetMapping("/users/dashboard/stats")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<AdminDashboardStatsDTO>> getDashboardStats() {
        AdminDashboardStatsDTO dto = adminDashboardService.buildStats();
        return ResponseEntity.ok(ApiResponse.success("Dashboard stats", dto));
    }

    @PostMapping("/employees")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<BaseResponseDTO> createEmployee(@RequestBody UserDTO userDTO) {
        if (userRepository.findByEmail(userDTO.getEmail()) != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new BaseResponseDTO("400", "Email already exists", null));
        }

        User user = new User();
        user.setEmail(userDTO.getEmail());
        user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        user.setMobileNumber(userDTO.getMobileNumber());
        user.setFirstName(userDTO.getFirstName());
        user.setLastName(userDTO.getLastName());
        user.setEmailVerified(true);
        user.setCreatedAt(LocalDateTime.now());
        user.setStatus(true);

        Set<Role> roles = new HashSet<>();

        String roleName = userDTO.getRole();
        if (roleName != null && !roleName.isEmpty()) {
            Role role = roleRepository.findByName(roleName.toUpperCase());
            if (role != null) {
                roles.add(role);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new BaseResponseDTO("400", "Invalid Role. Use SURVEYOR, LAB_TECHNICIAN, or MANAGER", null));
            }
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new BaseResponseDTO("400", "Role is required (SURVEYOR, LAB_TECHNICIAN, or MANAGER)", null));
        }
        user.setRoles(roles);

        userRepository.save(user);

        return ResponseEntity.ok(new BaseResponseDTO("200", "Employee created successfully", null));
    }

    @GetMapping("/employees")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<List<UserDTO>> getAllEmployees() {

        List<User> users = userRepository.findStaffRolesForAdminList();
        List<UserDTO> employees = users.stream()
                .filter(u -> u.getRoles().stream()
                        .anyMatch(r -> r.getName().equals("SURVEYOR") || r.getName().equals("LAB_TECHNICIAN")
                                || r.getName().equals("ADMIN")))
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(employees);
    }

    @PutMapping("/employees/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<BaseResponseDTO> updateEmployee(@PathVariable Long userId, @RequestBody UserDTO userDTO) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new BaseResponseDTO("404", "User not found", null));
        }
        User user = userOpt.get();
        user.setFirstName(userDTO.getFirstName());
        user.setLastName(userDTO.getLastName());
        user.setMobileNumber(userDTO.getMobileNumber());
        if (userDTO.getPassword() != null && !userDTO.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        }

        if (userDTO.getRole() != null) {
            Role role = roleRepository.findByName(userDTO.getRole().toUpperCase());
            if (role != null) {
                user.setRoles(Collections.singleton(role));
            }
        }

        userRepository.save(user);
        return ResponseEntity.ok(new BaseResponseDTO("200", "Employee updated successfully", null));
    }

    @DeleteMapping("/employees/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<BaseResponseDTO> deactivateEmployee(@PathVariable Long userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new BaseResponseDTO("404", "User not found", null));
        }
        User user = userOpt.get();
        user.setStatus(false);

        userRepository.save(user);
        return ResponseEntity.ok(new BaseResponseDTO("200", "Employee deactivated successfully", null));
    }

    private UserDTO convertToDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setUserId(user.getUserId());
        dto.setEmail(user.getEmail());
        dto.setMobileNumber(user.getMobileNumber());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        if (!user.getRoles().isEmpty()) {
            dto.setRole(user.getRoles().iterator().next().getName());
        }
        return dto;
    }
}
