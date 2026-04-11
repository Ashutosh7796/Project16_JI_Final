package com.spring.jwt.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

/**
 * Swagger/OpenAPI Configuration
 * Creates separate API documentation groups for User APIs and Admin APIs
 */
@Configuration
public class SwaggerConfig {

    /**
     * Main OpenAPI configuration
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("BitFlirt API Documentation")
                        .description("Comprehensive API documentation for BitFlirt matrimonial platform")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("BitFlirt Development Team")
                                .email("dev@bitflirt.com")
                                .url("https://bitflirt.com"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://bitflirt.com/license")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Development Server"),
                        new Server().url("https://api.bitflirt.com").description("Production Server")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT token for authentication")))
                .tags(Arrays.asList(
                        new Tag().name("Authentication").description("User authentication and authorization"),
                        new Tag().name("User Management").description("User registration and profile management"),
                        new Tag().name("Profile Management").description("User profile operations"),
                        new Tag().name("Horoscope Details").description("Horoscope and astrological information"),
                        new Tag().name("Education & Profession").description("Education and professional details"),
                        new Tag().name("Family Background").description("Family background information"),
                        new Tag().name("Partner Preference").description("Partner preference settings"),
                        new Tag().name("Contact Details").description("Contact information management"),
                        new Tag().name("Document Management").description("Document upload and verification"),
                        new Tag().name("Complete Profile").description("Complete profile workflow"),
                        new Tag().name("User View").description("User viewing and matching"),
                        new Tag().name("Subscription").description("Subscription management"),
                        
                        new Tag().name("Admin User Management").description("Administrative user operations"),
                        new Tag().name("Admin Profile Management").description("Administrative profile operations"),
                        new Tag().name("Admin Horoscope Management").description("Administrative horoscope operations"),
                        new Tag().name("Admin Education Management").description("Administrative education operations"),
                        new Tag().name("Admin Family Background Management").description("Administrative family background operations"),
                        new Tag().name("Admin Partner Preference Management").description("Administrative partner preference operations"),
                        new Tag().name("Admin Contact Details Management").description("Administrative contact details operations"),
                        new Tag().name("Admin Document Management").description("Administrative document operations"),
                        new Tag().name("Admin Complete Profile Management").description("Administrative complete profile operations"),
                        new Tag().name("Admin Registration Workflow").description("Administrative registration workflow orchestration")
                ));
    }

    /**
     * User APIs Group - Regular user-facing APIs
     */
    @Bean
    public GroupedOpenApi userApis() {
        return GroupedOpenApi.builder()
                .group("User APIs")
                .displayName("🧑‍💼 User APIs")
                .pathsToMatch(
                        "/api/auth/**",
                        "/api/v1/users/**",
                        "/api/v1/profiles/**",
                        "/api/v1/horoscope/**",
                        "/api/v1/education/**",
                        "/api/v1/family-background/**",
                        "/api/v1/partner-preference/**",
                        "/api/v1/contact-details/**",
                        "/api/v1/documents/**",
                        "/api/v1/complete-profile/**",
                        "/api/v1/user-view/**",
                        "/api/v1/subscription/**",
                        "/api/public/**"
                )
                .pathsToExclude("/api/v1/admin/**")
                .build();
    }

    /**
     * Admin APIs Group - Administrative APIs requiring ADMIN role
     */
    @Bean
    public GroupedOpenApi adminApis() {
        return GroupedOpenApi.builder()
                .group("Admin APIs")
                .displayName("🔐 Admin APIs")
                .pathsToMatch("/api/v1/admin/**")
                .build();
    }

    /**
     * All APIs Group - Complete API documentation (excluding admin APIs)
     */
    @Bean
    public GroupedOpenApi allApis() {
        return GroupedOpenApi.builder()
                .group("All APIs")
                .displayName("📚 All APIs")
                .pathsToMatch("/**")
                .pathsToExclude("/api/v1/admin/**")
                .build();
    }

    /**
     * Public APIs Group - APIs that don't require authentication
     */
    @Bean
    public GroupedOpenApi publicApis() {
        return GroupedOpenApi.builder()
                .group("Public APIs")
                .displayName("🌐 Public APIs")
                .pathsToMatch(
                        "/api/auth/login",
                        "/api/auth/refresh",
                        "/api/v1/users/register",
                        "/api/v1/users/password/**",
                        "/api/public/**",
                        "/reset-password",
                        "/reset-password-test"
                )
                .build();
    }
}