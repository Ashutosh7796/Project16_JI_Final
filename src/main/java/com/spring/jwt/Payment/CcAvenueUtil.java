//package com.spring.jwt.Payment;
//
//import javax.crypto.Cipher;
//import javax.crypto.spec.SecretKeySpec;
//import java.util.Base64;
//
//public class CcAvenueUtil {
//
//    private static final String ALGO = "AES/ECB/PKCS5Padding";
//
//    // 🔐 Encrypt
//    public static String encrypt(String plainText, String key) {
//        try {
//            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes("UTF-8"), "AES");
//
//            Cipher cipher = Cipher.getInstance(ALGO);
//            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
//
//            byte[] encrypted = cipher.doFinal(plainText.getBytes("UTF-8"));
//
//            return Base64.getEncoder().encodeToString(encrypted);
//
//        } catch (Exception e) {
//            throw new RuntimeException("Encryption failed", e);
//        }
//    }
//
//    // 🔓 Decrypt
//    public static String decrypt(String encryptedText, String key) {
//        try {
//            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes("UTF-8"), "AES");
//
//            Cipher cipher = Cipher.getInstance(ALGO);
//            cipher.init(Cipher.DECRYPT_MODE, keySpec);
//
//            byte[] decoded = Base64.getDecoder().decode(encryptedText);
//
//            return new String(cipher.doFinal(decoded), "UTF-8");
//
//        } catch (Exception e) {
//            throw new RuntimeException("Decryption failed", e);
//        }
//    }
//}
package com.spring.jwt.Payment;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;

public class CcAvenueUtil {

    // 🔐 Encrypt (CCAvenue Standard)
    public static String encrypt(String plainText, String workingKey) {
        try {
            // Step 1: MD5 of working key
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(workingKey.getBytes("UTF-8"));
            // Step 2: Use first 16 bytes as key
            SecretKeySpec secretKey = new SecretKeySpec(digest, "AES");
            // Step 3: ZERO IV
            IvParameterSpec iv = new IvParameterSpec(new byte[16]);
            // Step 4: AES/CBC
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, iv);
            byte[] encrypted = cipher.doFinal(plainText.getBytes("UTF-8"));
            // Step 5: HEX output
            return bytesToHex(encrypted);

        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    // 🔓 Decrypt
    public static String decrypt(String encryptedText, String workingKey) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(workingKey.getBytes("UTF-8"));

            SecretKeySpec secretKey = new SecretKeySpec(digest, "AES");
            IvParameterSpec iv = new IvParameterSpec(new byte[16]);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, iv);

            byte[] decrypted = cipher.doFinal(hexToBytes(encryptedText));

            return new String(decrypted, "UTF-8");

        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    // 🔁 HEX helpers
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] =
                    (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                            + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}