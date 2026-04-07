package com.spring.jwt.Employee;

import lombok.Data;

@Data
public class EmployeeUpdateRequestDTO {

    private String email;
    private String phone;
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
}

