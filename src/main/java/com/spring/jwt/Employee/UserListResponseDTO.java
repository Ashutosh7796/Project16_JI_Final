package com.spring.jwt.Employee;


import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserListResponseDTO {

    private Long userId;
    private String name;
    private String email;
    private Long mobile;
    private Boolean accountLocked;
    private String employeeCode;
}
