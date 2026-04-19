package com.spring.jwt.useraddress.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpsertSavedAddressRequest {

    @Size(max = 80)
    private String label;

    @NotBlank
    @Size(max = 120)
    private String fullName;

    @NotBlank
    @Size(max = 32)
    private String phone;

    @NotBlank
    @Size(max = 255)
    private String addressLine1;

    @Size(max = 255)
    private String addressLine2;

    @NotBlank
    @Size(max = 120)
    private String city;

    @NotBlank
    @Size(max = 120)
    private String state;

    @NotBlank
    @Pattern(regexp = "^\\d{6}$", message = "PIN must be 6 digits")
    private String pincode;

    @Size(max = 80)
    private String country;

    private boolean defaultAddress;
}
