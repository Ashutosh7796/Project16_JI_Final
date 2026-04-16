package com.spring.jwt.jwt.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.io.SerializationException;
import io.jsonwebtoken.io.Serializer;

import java.util.Map;

/**
 * Serializes JWT claim maps to UTF-8 JSON bytes for JJWT, using an isolated {@link ObjectMapper}
 * (not Spring's primary bean) with standard UTF-8 encoding (no character escaping).
 */
public final class Utf8JwtClaimsSerializer implements Serializer<Map<String, ?>> {

    public static final Utf8JwtClaimsSerializer INSTANCE = new Utf8JwtClaimsSerializer();

    private static final ObjectMapper MAPPER;

    static {
        // Create a fresh ObjectMapper with NO special encoding/escaping
        MAPPER = new ObjectMapper();
        // Explicitly disable ESCAPE_NON_ASCII to prevent corruption
        MAPPER.getFactory().configure(JsonWriteFeature.ESCAPE_NON_ASCII.mappedFeature(), false);
    }

    private Utf8JwtClaimsSerializer() {
    }

    static ObjectMapper mapper() {
        return MAPPER;
    }

    @Override
    public byte[] serialize(Map<String, ?> claims) throws SerializationException {
        try {
            byte[] result = MAPPER.writeValueAsBytes(claims);
            // Verify the serialized JSON does not contain escaped unicode sequences
            String json = new String(result, java.nio.charset.StandardCharsets.UTF_8);
            if (json.contains("\\u")) {
                throw new SerializationException("JWT claims contain escaped Unicode sequences - this indicates a serialization bug");
            }
            return result;
        } catch (JsonProcessingException e) {
            throw new SerializationException("Failed to serialize JWT claims to UTF-8 JSON", e);
        }
    }
}
