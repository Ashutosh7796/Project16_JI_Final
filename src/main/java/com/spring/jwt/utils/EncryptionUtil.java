package com.spring.jwt.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class EncryptionUtil {

    private final String primaryKey;
    private final List<String> legacyKeys = new ArrayList<>();
    private static final Pattern BASE64_PATTERN = Pattern.compile("^[A-Za-z0-9+/=]+$");
    
    public EncryptionUtil(
            @Value("${app.encryption.secret-key:defaultSecretKey12345678901234567890}") String primaryKey,
            @Value("${app.encryption.legacy-keys:}") String legacyKeysStr) {

        this.primaryKey = normalizeKey(primaryKey);

        if (legacyKeysStr != null && !legacyKeysStr.isEmpty()) {
            String[] keys = legacyKeysStr.split(",");
            for (String key : keys) {
                if (key != null && !key.trim().isEmpty()) {
                    legacyKeys.add(normalizeKey(key.trim()));
                }
            }
        }

        legacyKeys.add(normalizeKey("secure-encryption-key-123456789012"));
        legacyKeys.add(normalizeKey("secure-field-encryption-key-456"));
        legacyKeys.add(normalizeKey("fieldEncryptionKey123"));
    }
    
    private String normalizeKey(String key) {
        if (key.length() < 32) {
            return String.format("%-32s", key).replace(' ', '0');
        } else if (key.length() > 32) {
            return key.substring(0, 32);
        }
        return key;
    }

    public String encrypt(String data) {
        if (data == null || data.isEmpty()) {
            return data;
        }

        if (isLikelyEncrypted(data)) {
            return data;
        }
        
        try {
            SecretKeySpec secretKeySpec = new SecretKeySpec(primaryKey.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
            
            byte[] encryptedBytes = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            throw new RuntimeException("Error encrypting data", e);
        }
    }
    
    public String decrypt(String encryptedData) {
        if (encryptedData == null || encryptedData.isEmpty()) {
            return encryptedData;
        }

        Exception lastException = null;
        try {
            return decryptWithKey(encryptedData, primaryKey);
        } catch (Exception e) {
            lastException = e;
        }

        for (String legacyKey : legacyKeys) {
            try {
                return decryptWithKey(encryptedData, legacyKey);
            } catch (Exception e) {
                lastException = e;
            }
        }

        return encryptedData;
    }
    
    private String decryptWithKey(String encryptedData, String key) throws Exception {
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
        
        byte[] decodedBytes = Base64.getDecoder().decode(encryptedData);
        byte[] decryptedBytes = cipher.doFinal(decodedBytes);
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }
    
    /**
     * Check if a string is likely to be encrypted (Base64 encoded)
     * This is a heuristic - not 100% accurate but helpful to avoid double encryption
     */
    private boolean isLikelyEncrypted(String data) {
        if (data.length() < 10) {
            return false;
        }

        return BASE64_PATTERN.matcher(data).matches();
    }
} 