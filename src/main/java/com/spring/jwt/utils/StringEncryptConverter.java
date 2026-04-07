package com.spring.jwt.utils;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Converter
@Component
@Slf4j
public class StringEncryptConverter implements AttributeConverter<String, String> {

    private final EncryptionUtil encryptionUtil;

    @Autowired
    public StringEncryptConverter(EncryptionUtil encryptionUtil) {
        this.encryptionUtil = encryptionUtil;
        log.info("StringEncryptConverter initialized");
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return attribute;
        }
        
        try {
            log.debug("Encrypting data for storage");
            return encryptionUtil.encrypt(attribute);
        } catch (Exception e) {
            log.error("Error encrypting data: {}", e.getMessage());
            return attribute;
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return dbData;
        }
        
        try {
            log.debug("Decrypting data from storage");
            return encryptionUtil.decrypt(dbData);
        } catch (Exception e) {
            log.warn("Unable to decrypt data, assuming it's plain text: {}", e.getMessage());
            return dbData;
        }
    }
} 