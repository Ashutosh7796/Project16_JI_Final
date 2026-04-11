package com.spring.jwt.service.impl;

import com.spring.jwt.dto.DeviceInfo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
@Slf4j
public class DeviceFingerprintService {

    public String generateFingerprint(HttpServletRequest request, DeviceInfo deviceInfo) {
        try {
            String fingerprintData = buildFingerprintData(request, deviceInfo);
            return hashFingerprint(fingerprintData);
        } catch (NoSuchAlgorithmException e) {
            log.error("Error generating device fingerprint: {}", e.getMessage());
            return null;
        }
    }

    private String buildFingerprintData(HttpServletRequest request, DeviceInfo deviceInfo) {
        StringBuilder fingerprint = new StringBuilder();

        fingerprint.append(getClientIpAddress(request))
                  .append("|")
                  .append(getHeaderValue(request, "User-Agent"))
                  .append("|")
                  .append(getHeaderValue(request, "Accept-Language"))
                  .append("|")
                  .append(getHeaderValue(request, "Accept-Encoding"));

        if (deviceInfo != null) {
            fingerprint.append("|")
                      .append(getDeviceInfoValue(deviceInfo.getScreenResolution()))
                      .append("|")
                      .append(getDeviceInfoValue(deviceInfo.getTimezone()))
                      .append("|")
                      .append(getDeviceInfoValue(deviceInfo.getPlatform()));
        }

        return fingerprint.toString();
    }

    private String hashFingerprint(String fingerprintData) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(fingerprintData.getBytes(StandardCharsets.UTF_8));
        
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        
        return hexString.toString();
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(xRealIp)) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }

    private String getHeaderValue(HttpServletRequest request, String headerName) {
        String headerValue = request.getHeader(headerName);
        return headerValue != null ? headerValue : "";
    }

    private String getDeviceInfoValue(String value) {
        return value != null ? value : "";
    }
}