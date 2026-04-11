package com.spring.jwt.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeviceInfo {
    private String userAgent;
    private String ipAddress;
    private String acceptLanguage;
    private String acceptEncoding;
    private String screenResolution;
    private String timezone;
    private String platform;
    private String deviceType;
}