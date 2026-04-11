package com.spring.jwt.Attendance.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Check-Out Request DTO
 * Used when employee checks out
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckOutRequest {

    private Long userId;

    @NotNull(message = "Date is required")
    @PastOrPresent(message = "Date cannot be in the future")
    private LocalDate date;

    @Valid
    @NotNull(message = "Location is required for check-out")
    private LocationDTO location;

    private String remarks;
}
