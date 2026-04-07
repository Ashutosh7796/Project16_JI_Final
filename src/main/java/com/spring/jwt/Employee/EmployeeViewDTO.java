package com.spring.jwt.Employee;

import lombok.Data;

@Data
public class EmployeeViewDTO {

    private Long employeeId;
    private String employeeCode;
    private Long userId;
    private String userEmail;
    private String city;
    private String district;
    private String state;
}
