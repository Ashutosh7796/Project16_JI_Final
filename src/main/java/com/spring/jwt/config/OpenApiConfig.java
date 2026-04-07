package com.spring.jwt.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for OpenAPI documentation
 */
@Configuration
public class OpenApiConfig
{

    private static final String SECURITY_SCHEME_NAME = "Bearer Authentication";

    /**
     * Configure OpenAPI documentation with JWT authentication
     */
    @Bean
    public OpenAPI openAPI()
    {
        return new OpenAPI()
                .info(apiInfo())
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(
                        new Components()
                                .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                        new SecurityScheme()
                                                .name("Bearer Authentication")
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")
                                                .description("Enter JWT token with Bearer prefix. Example: 'Bearer eyJhbGciOiJIUzI1NiJ9...'")
                                )
                );
    }

    /**
     * Configure API information for documentation
     */
    private Info apiInfo()
    {
        return new Info()
                .title("car API")
                .description("API documentation ")
                .version("1.0.0")
                .contact(
                        new Contact()
                                .name(" Development Team")
                                .url("https://.example.com")
                                .email("support@.example.com")
                )
                .license(
                        new License()
                                .name(" License")
                                .url("https://example.com/license")
                );
    }
} 