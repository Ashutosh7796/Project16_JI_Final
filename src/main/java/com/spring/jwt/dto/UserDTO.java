package com.spring.jwt.dto;

import com.spring.jwt.entity.Role;
import com.spring.jwt.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.stream.Collectors;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserDTO {

    @Schema(description = "userId of User", example = "10011")
    private Long userId;

    @Schema(description = "Email of User", example = "example@example.com")
    private String email;

    @Schema(description = "Mobile Number", example = "9822222212")
    private Long mobileNumber;

    @Schema(description = "Password", example = "Pass@1234")
    private String password;

    private String firstName;
    private String lastName;

    private String role;
    private Set<String> roles;

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


    public UserDTO(User user) {
        this.userId = user.getUserId();
        this.email = user.getEmail();
        this.mobileNumber = user.getMobileNumber();
        this.firstName = user.getFirstName();
        this.lastName = user.getLastName();

        if (user.getRoles() != null) {
            this.roles = user.getRoles().stream()
                    .map(Role::getName)
                    .collect(Collectors.toSet());
        }
    }

    public static UserDTO fromEntity(User user) {
        if (user == null) return null;
        return new UserDTO(user);
    }
}
