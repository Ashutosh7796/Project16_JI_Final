package com.spring.jwt.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Aggregated admin dashboard figures + 8-week order track series.
 * Field names align with the React admin dashboard expectations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardStatsDTO {
    /** Farmer survey registrations (employee–farmer forms). */
    private long totalUsers;
    /** Alias used by some frontend code paths. */
    private long farmers;
    private long employees;
    private long products;
    /** Total order-like records (pending + confirmed tables). */
    private long orders;
    private List<OrderWeekPointDTO> orderTrack;
}
