package com.spring.jwt.config;

import com.spring.jwt.Enums.DocumentType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Custom converter for DocumentType enum
 * Handles case-insensitive conversion from String to DocumentType
 * Supports both enum constant names and display names
 */
@Component
public class DocumentTypeConverter implements Converter<String, DocumentType> {

    @Override
    public DocumentType convert(String source) {
        if (source == null || source.trim().isEmpty()) {
            throw new IllegalArgumentException("Document type cannot be null or empty");
        }

        // Try to match by enum constant name (case-insensitive)
        for (DocumentType type : DocumentType.values()) {
            if (type.name().equalsIgnoreCase(source.trim())) {
                return type;
            }
        }

        // Try to match by display name (case-insensitive)
        for (DocumentType type : DocumentType.values()) {
            if (type.getDisplayName().equalsIgnoreCase(source.trim())) {
                return type;
            }
        }

        // If no match found, throw exception with helpful message
        throw new IllegalArgumentException(
            String.format("Invalid document type: '%s'. Valid types are: %s", 
                source, 
                String.join(", ", getValidTypeNames())
            )
        );
    }

    private String[] getValidTypeNames() {
        DocumentType[] types = DocumentType.values();
        String[] names = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            names[i] = types[i].name();
        }
        return names;
    }
}
