package com.spring.jwt.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One bar group in the admin "Order Track" chart (plan vs actual). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderWeekPointDTO {
    /** X-axis label, e.g. Wk 1 … Wk 8 */
    private String label;
    /** Target / plan level (raw count for the week; UI may normalize). */
    private long plan;
    /** Realised orders in the week (raw count). */
    private long actual;
}
