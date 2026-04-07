package com.spring.jwt.utils.EmailVerificationService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public class OtpUtil {

    private static final String HASH_ALGORITHM = "SHA-256";

    public static String generateOtp(int length) {
        SecureRandom random = new SecureRandom();
        StringBuilder otp = new StringBuilder();
        for (int i = 0; i < length; i++) {
            otp.append(random.nextInt(10));
        }
        return otp.toString();
    }

    public static String generateSalt() {
        byte[] salt = new byte[16];
        SecureRandom random;
        try {
            random = SecureRandom.getInstanceStrong();
        } catch (NoSuchAlgorithmException e) {
            random = new SecureRandom();
        }
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    public static String hashOtp(String otp, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hashedBytes = digest.digest((otp + salt).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashedBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Hashing algorithm not found: " + HASH_ALGORITHM, e);
        }
    }

    public static boolean verifyOtp(String inputOtp, String storedHashedOtp, String salt) {
        return hashOtp(inputOtp, salt).equals(storedHashedOtp);
    }

    public static void listAvailableAlgorithms() {
        System.out.println("Available MessageDigest algorithms:");
        for (java.security.Provider provider : java.security.Security.getProviders()) {
            provider.getServices().stream()
                    .filter(service -> "MessageDigest".equals(service.getType()))
                    .forEach(service -> System.out.println(service.getAlgorithm()));
        }
    }
}
