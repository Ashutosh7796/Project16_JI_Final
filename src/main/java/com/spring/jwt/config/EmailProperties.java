package com.spring.jwt.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Email Configuration Properties
 * 
 * Externalized configuration for email service
 * Configure in application.properties or application.yml
 */
@Configuration
@ConfigurationProperties(prefix = "app.email")
@Data
@Validated
public class EmailProperties {

    /**
     * SMTP Configuration
     */
    @NotBlank(message = "SMTP host is required")
    private String smtpHost = "smtp.gmail.com";

    @NotBlank(message = "SMTP port is required")
    private String smtpPort = "465";

    private boolean sslEnable = true;
    private boolean authEnable = true;

    /**
     * Sender Configuration
     */
    @NotBlank(message = "From email is required")
    @Email(message = "Invalid from email format")
    private String fromEmail;

    @NotBlank(message = "From name is required")
    private String fromName = "Support Team";

    /**
     * SMTP Authentication
     */
    @NotBlank(message = "SMTP username is required")
    private String username;

    @NotBlank(message = "SMTP password is required")
    private String password;

    /**
     * Email Templates
     */
    private String companyName = "Your Company";
    private String supportEmail = "support@yourcompany.com";
    private String websiteUrl = "https://yourcompany.com";

    /**
     * Retry Configuration
     */
    private int maxRetries = 3;
    private long retryDelayMs = 1000;
}
