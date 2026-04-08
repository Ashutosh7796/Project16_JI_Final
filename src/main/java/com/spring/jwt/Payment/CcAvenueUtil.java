package com.spring.jwt.Payment;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;

public class CcAvenueUtil {

    private CcAvenueUtil() {}

    public static String encrypt(String plainText, String workingKey) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(workingKey.getBytes("UTF-8"));
            SecretKeySpec secretKey = new SecretKeySpec(digest, "AES");
            IvParameterSpec iv = new IvParameterSpec(new byte[16]);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, iv);
            return bytesToHex(cipher.doFinal(plainText.getBytes("UTF-8")));
        } catch (Exception e) {
            throw new RuntimeException("CCAvenue encryption failed", e);
        }
    }

    public static String decrypt(String encryptedText, String workingKey) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(workingKey.getBytes("UTF-8"));
            SecretKeySpec secretKey = new SecretKeySpec(digest, "AES");
            IvParameterSpec iv = new IvParameterSpec(new byte[16]);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, iv);
            return new String(cipher.doFinal(hexToBytes(encryptedText)), "UTF-8");
        } catch (Exception e) {
            throw new RuntimeException("CCAvenue decryption failed", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
