package com.spring.jwt.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.spring.jwt.Enums.DocumentType;

import java.io.IOException;

/**
 * Custom JSON deserializer for DocumentType enum
 * Handles case-insensitive deserialization from JSON
 */
public class DocumentTypeDeserializer extends JsonDeserializer<DocumentType> {

    @Override
    public DocumentType deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String value = p.getText();
        
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Document type cannot be null or empty");
        }

        // Try to match by enum constant name (case-insensitive)
        for (DocumentType type : DocumentType.values()) {
            if (type.name().equalsIgnoreCase(value.trim())) {
                return type;
            }
        }

        // Try to match by display name (case-insensitive)
        for (DocumentType type : DocumentType.values()) {
            if (type.getDisplayName().equalsIgnoreCase(value.trim())) {
                return type;
            }
        }

        // If no match found, throw exception
        throw new IllegalArgumentException(
            String.format("Invalid document type: '%s'. Valid types are: %s", 
                value, 
                getValidTypeNames()
            )
        );
    }

    private String getValidTypeNames() {
        DocumentType[] types = DocumentType.values();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < types.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(types[i].name());
        }
        return sb.toString();
    }
}
