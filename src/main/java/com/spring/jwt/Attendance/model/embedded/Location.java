package com.spring.jwt.Attendance.model.embedded;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Location Embeddable
 * Represents GPS coordinates and address
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Location {

    @Column(precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(precision = 11, scale = 8)
    private BigDecimal longitude;

    @Column(length = 500)
    private String address;

    /**
     * Check if location data is valid
     */
    public boolean isValid() {
        return latitude != null && longitude != null;
    }

    /**
     * Get formatted coordinates string
     */
    public String getCoordinates() {
        if (latitude != null && longitude != null) {
            return String.format("%.6f, %.6f", latitude, longitude);
        }
        return null;
    }

    /**
     * Calculate distance from another location (in kilometers)
     * Using Haversine formula
     */
    public double distanceFrom(Location other) {
        if (!this.isValid() || !other.isValid()) {
            return -1;
        }

        final int EARTH_RADIUS = 6371; // Radius in kilometers

        double lat1Rad = Math.toRadians(this.latitude.doubleValue());
        double lat2Rad = Math.toRadians(other.latitude.doubleValue());
        double deltaLat = Math.toRadians(other.latitude.subtract(this.latitude).doubleValue());
        double deltaLon = Math.toRadians(other.longitude.subtract(this.longitude).doubleValue());

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                   Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                   Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS * c;
    }
}
