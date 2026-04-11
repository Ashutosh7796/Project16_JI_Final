package com.spring.jwt.config;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Operation;

import java.util.Map;

/**
 * Swagger UI Customization Configuration
 * Enhances the appearance and organization of API documentation
 */
@Configuration
public class SwaggerUIConfig {

    /**
     * Customizes OpenAPI documentation to enhance admin endpoints
     */
    @Bean
    public OpenApiCustomizer adminApiCustomizer() {
        return openApi -> {
            if (openApi.getPaths() != null) {
                openApi.getPaths().forEach((path, pathItem) -> {
                    if (path.startsWith("/api/v1/admin/"))
                        if (path.startsWith("/api/v1/survey/")) {
                        enhanceAdminEndpoint(pathItem);
                    }
                });
            }
        };
    }

    /**
     * Enhances admin endpoint documentation
     */
    private void enhanceAdminEndpoint(PathItem pathItem) {
        Map<PathItem.HttpMethod, Operation> operations = pathItem.readOperationsMap();
        
        operations.forEach((method, operation) -> {
            if (operation != null) {
                String summary = operation.getSummary();
                if (summary != null && !summary.contains("(Admin)")) {
                    operation.setSummary(summary + " (Admin)");
                }
                
                String description = operation.getDescription();
                if (description != null && !description.contains("Requires ADMIN role")) {
                    operation.setDescription(description + "\n\n**⚠️ Requires ADMIN role and valid JWT token.**");
                }
            }
        });
    }
}