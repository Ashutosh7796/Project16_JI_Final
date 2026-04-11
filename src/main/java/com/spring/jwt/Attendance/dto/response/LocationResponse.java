package com.spring.jwt.Attendance.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Location Response DTO
 * Used to return location information to frontend
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationResponse {

    private BigDecimal latitude;
    private BigDecimal longitude;
    private String address;
    
    /**
     * Get formatted coordinates
     */
    public String getCoordinates() {
        if (latitude != null && longitude != null) {
            return String.format("%.6f, %.6f", latitude, longitude);
        }
        return null;
    }
}
