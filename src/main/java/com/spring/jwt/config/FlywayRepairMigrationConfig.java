package com.spring.jwt.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Runs {@link Flyway#repair()} before {@link Flyway#migrate()} on startup.
 * <p>
 * Flyway validates applied migrations against file checksums before migrating; if an applied
 * script was edited locally, validation throws {@code FlywayValidateException} before migrate
 * (so {@code spring.flyway.repair-on-migrate} alone does not always help). Repair realigns
 * {@code flyway_schema_history} with the current files. Prefer never editing applied migrations;
 * add a new versioned script instead when possible.
 */
@Configuration
public class FlywayRepairMigrationConfig {

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            flyway.repair();
            flyway.migrate();
        };
    }
}
