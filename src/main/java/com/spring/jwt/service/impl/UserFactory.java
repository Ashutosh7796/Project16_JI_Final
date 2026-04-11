//package com.spring.jwt.service.impl;
//
//import com.spring.jwt.dto.AdminRegistrationRequest;
//import com.spring.jwt.dto.SecureUserRegistrationRequest;
//import com.spring.jwt.entity.User;
//import jakarta.servlet.http.HttpServletRequest;
//import lombok.RequiredArgsConstructor;
//import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
//import org.springframework.stereotype.Component;
//
//@Component
//@RequiredArgsConstructor
//public class UserFactory {
//
//    private final BCryptPasswordEncoder passwordEncoder;
//
//    public User createUser(SecureUserRegistrationRequest request, HttpServletRequest httpRequest)
//    {
//        return buildUser(
//            request.getEmail(),
//            request.getPassword(),
//            request.getMobileNumber(),
//            request.getFirstName(),
//            request.getLastName(),
//            false,
//            request.getAcceptTerms(),
//            request.getAcceptPrivacyPolicy()
//        );
//    }
//
//    public User createAdmin(AdminRegistrationRequest request, HttpServletRequest httpRequest)
//    {
//        return buildUser(
//            request.getEmail(),
//            request.getPassword(),
//            request.getMobileNumber(),
//            request.getFirstName(),
//            request.getLastName(),
//            true,
//            true,
//            true
//        );
//    }
//
//    private User buildUser(String email, String password, Long mobileNumber,
//                          String firstName, String lastName, boolean emailVerified,
//                          boolean termsAccepted, boolean privacyPolicyAccepted)
//    {
//        User user = new User();
//        user.setEmail(email.toLowerCase().trim());
//        user.setPassword(passwordEncoder.encode(password));
//        user.setMobileNumber(mobileNumber);
//        user.setFirstName(firstName.trim());
//        user.setLastName(lastName.trim());
//        user.setEmailVerified(emailVerified);
//        user.setTermsAccepted(termsAccepted);
//        user.setPrivacyPolicyAccepted(privacyPolicyAccepted);
//        user.setAccountLocked(false);
//        user.setFailedLoginAttempts(0);
//        return user;
//    }
//}

package com.spring.jwt.service.impl;

import com.spring.jwt.Employee.EmployeeRepository;
import com.spring.jwt.dto.AdminRegistrationRequest;
import com.spring.jwt.dto.SecureUserRegistrationRequest;
import com.spring.jwt.entity.Employee;
import com.spring.jwt.entity.Role;
import com.spring.jwt.entity.User;
import com.spring.jwt.repository.RoleRepository;
import com.spring.jwt.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class UserFactory {

    private final BCryptPasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final EmployeeRepository employeeRepository;

    /* ===================== PUBLIC USER REGISTRATION ===================== */

    @Transactional
    public User createUser(SecureUserRegistrationRequest request,
                           HttpServletRequest httpRequest) {

        // BUILD USER
        User user = buildUser(
                request.getEmail(),
                request.getPassword(),
                request.getMobileNumber(),
                request.getFirstName(),
                request.getLastName(),
                false,
                request.getAcceptTerms(),
                request.getAcceptPrivacyPolicy()
        );

        /* ===================== ROLE LOGIC ===================== */

        // Default to USER role for public registration
        String roleName = "USER";
        
        // If role is explicitly provided, validate it
        if (request.getRole() != null && !request.getRole().isBlank()) {
            roleName = request.getRole().toUpperCase();
            
            // Block ADMIN role - requires special registration endpoint
            if ("ADMIN".equals(roleName)) {
                throw new AccessDeniedException("ADMIN role cannot be assigned through public registration");
            }
            
            // MANAGER role requires ADMIN authorization
            if ("MANAGER".equals(roleName)) {
                validateManagerRegistration();
            }
        }

        Role role = roleRepository.findByName(roleName);
        if (role == null) {
            throw new IllegalArgumentException("Invalid role: " + roleName);
        }

        user.setRoles(new java.util.HashSet<>(Set.of(role)));

        user = userRepository.save(user);

        /* ===================== CREATE EMPLOYEE FOR STAFF ===================== */

        if ("SURVEYOR".equals(roleName) || "LAB_TECHNICIAN".equals(roleName)) {
            createEmployee(user, request);
        }
        
        // MANAGER role requires ADMIN authorization (already validated above)
        if ("MANAGER".equals(roleName)) {
            createEmployee(user, request);
        }

        return user;
    }

    /**
     * Validates that only ADMIN users can register MANAGER role
     */
    private void validateManagerRegistration() {
        Authentication authentication = 
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required to register MANAGER role");
        }
        
        boolean isAdmin = authentication.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        
        if (!isAdmin) {
            throw new AccessDeniedException("Only ADMIN users can register MANAGER role");
        }
    }

    /* ===================== ADMIN REGISTRATION ===================== */

    @Transactional
    public User createAdmin(AdminRegistrationRequest request,
                            HttpServletRequest httpRequest) {

        User user = buildUser(
                request.getEmail(),
                request.getPassword(),
                request.getMobileNumber(),
                request.getFirstName(),
                request.getLastName(),
                true,
                true,
                true
        );

        Role adminRole = roleRepository.findByName("ADMIN");
        user.setRoles(new java.util.HashSet<>(Set.of(adminRole)));

        return userRepository.save(user);
    }

    /* ===================== INTERNAL HELPERS ===================== */

    private User buildUser(String email, String password, Long mobileNumber,
                           String firstName, String lastName,
                           boolean emailVerified,
                           boolean termsAccepted,
                           boolean privacyPolicyAccepted) {

        User user = new User();
        user.setEmail(email.toLowerCase().trim());
        user.setPassword(passwordEncoder.encode(password));
        user.setMobileNumber(mobileNumber);
        user.setFirstName(firstName != null ? firstName.trim() : null);
        user.setLastName(lastName != null ? lastName.trim() : null);
        user.setEmailVerified(emailVerified);
        user.setTermsAccepted(termsAccepted);
        user.setPrivacyPolicyAccepted(privacyPolicyAccepted);
        user.setAccountLocked(false);
        user.setFailedLoginAttempts(0);
        return user;
    }

    /* ===================== EMPLOYEE CREATION ===================== */

    private void createEmployee(User user, SecureUserRegistrationRequest request) {

        Employee employee = new Employee();
        employee.setUser(user);

        employee.setEmployeeCode(
                request.getEmployeeCode() != null
                        ? request.getEmployeeCode()
                        : "EMP-" + user.getUserId()
        );

        employee.setCompanyName(request.getCompanyName());
        employee.setAddress(request.getAddress());
        employee.setPermanentAddress(request.getPermanentAddress());
        employee.setCity(request.getCity());
        employee.setDistrict(request.getDistrict());
        employee.setState(request.getState());
        employee.setAccountNumber(request.getAccountNumber());
        employee.setIfscCode(request.getIfscCode());
        employee.setPfNumber(request.getPfNumber());
        employee.setInsuranceNumber(request.getInsuranceNumber());
        employee.setPanNumber(request.getPanNumber());
        employee.setVehicleNumber(request.getVehicleNumber());
        employee.setDescription(request.getDescription());

        employeeRepository.save(employee);
    }
}
