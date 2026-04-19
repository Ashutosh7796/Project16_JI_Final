package com.spring.jwt.useraddress.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SavedAddressResponse {
    private Long id;
    private String label;
    private String fullName;
    private String phone;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String pincode;
    private String country;
    private boolean defaultAddress;
}
