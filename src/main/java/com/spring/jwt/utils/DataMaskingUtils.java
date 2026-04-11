package com.spring.jwt.utils;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Utility class for masking sensitive data in logs and responses
 */
@Component
public class DataMaskingUtils {

    /**
     * Masks an email address, showing only the first character and domain
     * e.g., j***@example.com
     */
    public static String maskEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return email;
        }
        
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return email;
        }
        
        String username = email.substring(0, atIndex);
        String domain = email.substring(atIndex);

        String maskedUsername = username.charAt(0) + 
                "*".repeat(Math.min(username.length() - 1, 3)) + 
                (username.length() > 4 ? "..." : "");
        
        return maskedUsername + domain;
    }
    
    /**
     * Masks a mobile number, showing only the last 4 digits
     * e.g., *******1234
     */
    public static String maskPhoneNumber(String phoneNumber) {
        if (!StringUtils.hasText(phoneNumber)) {
            return phoneNumber;
        }
        
        if (phoneNumber.length() <= 4) {
            return phoneNumber;
        }
        
        return "*".repeat(phoneNumber.length() - 4) + 
                phoneNumber.substring(phoneNumber.length() - 4);
    }
    
    /**
     * Masks a mobile number, showing only the last 4 digits
     */
    public static String maskPhoneNumber(Long phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }
        
        return maskPhoneNumber(phoneNumber.toString());
    }
    
    /**
     * Masks an address to show only the street number and city
     * e.g., 123 *****, City
     */
    public static String maskAddress(String address) {
        if (!StringUtils.hasText(address)) {
            return address;
        }

        String[] parts = address.split(" ");
        if (parts.length <= 2) {
            return address;
        }
        StringBuilder masked = new StringBuilder(parts[0]);
        masked.append(" ***** ");
        masked.append(parts[parts.length - 1]);
        
        return masked.toString();
    }
    
    /**
     * Masks a JWT token, showing only the first 8 characters
     * e.g., eyJhbGci...
     */
    public static String maskToken(String token) {
        if (!StringUtils.hasText(token)) {
            return token;
        }
        
        if (token.length() <= 12) {
            return token;
        }
        
        return token.substring(0, 8) + "...";
    }
    
    /**
     * Masks a credit card number, showing only the last 4 digits
     * e.g., **** **** **** 1234
     */
    public static String maskCreditCard(String cardNumber) {
        if (!StringUtils.hasText(cardNumber)) {
            return cardNumber;
        }

        String digitsOnly = cardNumber.replaceAll("\\D", "");
        
        if (digitsOnly.length() < 4) {
            return cardNumber;
        }
        
        String lastFourDigits = digitsOnly.substring(digitsOnly.length() - 4);
        StringBuilder masked = new StringBuilder();

        for (int i = 0; i < digitsOnly.length() - 4; i++) {
            masked.append("*");
            if ((i + 1) % 4 == 0) {
                masked.append(" ");
            }
        }

        if (masked.length() > 0 && masked.charAt(masked.length() - 1) != ' ') {
            masked.append(" ");
        }
        
        masked.append(lastFourDigits);
        return masked.toString();
    }
} 