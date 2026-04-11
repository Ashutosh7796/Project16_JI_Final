package com.spring.jwt.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC Configuration
 * Registers custom converters and formatters
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final DocumentTypeConverter documentTypeConverter;

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(documentTypeConverter);
    }
}
