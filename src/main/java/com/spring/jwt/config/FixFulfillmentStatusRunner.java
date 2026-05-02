package com.spring.jwt.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FixFulfillmentStatusRunner {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void fixFulfillmentStatusColumn() {
        try {
            log.info("Checking fulfillment_status column type...");
            // Force the column to be VARCHAR instead of ENUM so that new statuses like SHIPPED and DELIVERED work.
            jdbcTemplate.execute("ALTER TABLE checkout_order_lines MODIFY COLUMN fulfillment_status VARCHAR(30) NOT NULL DEFAULT 'PENDING'");
            log.info("Successfully ensured fulfillment_status is VARCHAR(30).");
        } catch (Exception e) {
            log.warn("Could not execute ALTER TABLE for fulfillment_status (it may already be correct, or table doesn't exist yet): {}", e.getMessage());
        }
    }
}
