package com.spring.jwt.Payment;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;

public class CcAvenueUtil {

    private CcAvenueUtil() {}

    private static final byte[] CCAVENUE_IV = {
            0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
            0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f
    };

    public static String encrypt(String plainText, String workingKey) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(workingKey.getBytes("UTF-8"));
            SecretKeySpec secretKey = new SecretKeySpec(digest, "AES");
            IvParameterSpec iv = new IvParameterSpec(CCAVENUE_IV);
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
            IvParameterSpec iv = new IvParameterSpec(CCAVENUE_IV);
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
