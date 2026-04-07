package com.spring.jwt.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "employee", indexes = {@Index(name = "idx_employee_user_id", columnList = "user_id")}
)
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long employeeId;


    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            unique = true
    )
    private User user;

    @Column(name = "employee_code", unique = true)
    private String employeeCode;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "permanent_address", length = 500)
    private String permanentAddress;

    @Column(name = "city")
    private String city;

    @Column(name = "district")
    private String district;

    @Column(name = "state")
    private String state;

    @Column(name = "account_number")
    private String accountNumber;

    @Column(name = "ifsc_code")
    private String ifscCode;

    @Column(name = "pf_number")
    private String pfNumber;

    @Column(name = "insurance_number")
    private String insuranceNumber;

    @Column(name = "pan_number")
    private String panNumber;

    @Column(name = "vehicle_number")
    private String vehicleNumber;

    @Column(name = "description", length = 1000)
    private String description;
}
