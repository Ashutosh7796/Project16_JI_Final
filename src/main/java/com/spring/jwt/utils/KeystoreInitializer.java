package com.spring.jwt.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationContextInitializedEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Automatically generates a keystore.p12 file if it doesn't exist
 * This runs BEFORE application context initialization to ensure SSL can be configured
 */
@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
public class KeystoreInitializer {

    private static final Logger logger = LoggerFactory.getLogger(KeystoreInitializer.class);

    private static final String KEYSTORE_PASSWORD = "yourpassword";
    private static final String KEY_ALIAS = "youralias";
    
    /**
     * Initialize keystore immediately during class loading
     * This happens before the Spring context even starts to load
     */
    static {
        try {
            initializeKeystore();
        } catch (Exception e) {
            System.err.println("Failed to initialize keystore: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Generate the keystore.p12 file if it doesn't exist
     */
    private static void initializeKeystore() throws Exception {
        // Get current working directory
        String currentDir = System.getProperty("user.dir");
        Path resourcesPath = Paths.get(currentDir, "src", "main", "resources");
        Path keystorePath = resourcesPath.resolve("keystore.p12");
        
        // Check if keystore already exists
        if (Files.exists(keystorePath)) {
            logger.info("Keystore already exists at: {}", keystorePath.toAbsolutePath());
            ensureKeystoreInClasspath(keystorePath);
            return;
        }
        
        logger.info("Keystore not found, generating a new one");

        // Create resources directory if it doesn't exist
        if (!Files.exists(resourcesPath)) {
            Files.createDirectories(resourcesPath);
            logger.info("Created resources directory at: {}", resourcesPath.toAbsolutePath());
        }
        
        logger.info("Generating new keystore at: {}", keystorePath.toAbsolutePath());

        // Build command as array to avoid issues with spaces and special characters
        String[] command = {
            "keytool", 
            "-genkeypair", 
            "-alias", KEY_ALIAS, 
            "-keyalg", "RSA", 
            "-keysize", "2048", 
            "-storetype", "PKCS12", 
            "-keystore", keystorePath.toString(), 
            "-validity", "3650", 
            "-storepass", KEYSTORE_PASSWORD, 
            "-keypass", KEYSTORE_PASSWORD, 
            "-dname", "CN=localhost,OU=AutoCarCare,O=AutoCarCare,L=City,ST=State,C=US",
            "-noprompt"
        };
        
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        Process process = processBuilder.start();
        
        int exitCode = process.waitFor();
        
        if (exitCode == 0) {
            logger.info("Keystore generated successfully at: {}", keystorePath.toAbsolutePath());
            ensureKeystoreInClasspath(keystorePath);
        } else {
            logger.error("Failed to generate keystore. Exit code: {}", exitCode);

            byte[] errorBytes = process.getErrorStream().readAllBytes();
            if (errorBytes.length > 0) {
                logger.error("Error from keytool: {}", new String(errorBytes));
            }
            
            throw new RuntimeException("Failed to generate keystore");
        }
    }
    
    /**
     * Ensure the keystore is available in the classpath for the current run
     */
    private static void ensureKeystoreInClasspath(Path keystorePath) throws IOException {

        File classesDir = new File("target/classes");
        if (!classesDir.exists()) {
            classesDir.mkdirs();
        }
        
        Path targetPath = Paths.get("target/classes/keystore.p12");
        Files.copy(keystorePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        logger.debug("Keystore copied to classpath at: {}", targetPath.toAbsolutePath());
    }
    
    /**
     * Another safety check during context initialization
     */
    @PostConstruct
    public void onInit() {
        try {
            Path keystorePath = Paths.get("src", "main", "resources", "keystore.p12");
            if (!Files.exists(keystorePath)) {
                logger.warn("Keystore still not found after static initialization. This shouldn't happen.");
                try {
                    initializeKeystore();
                } catch (Exception e) {
                    logger.error("Failed to initialize keystore: {}", e.getMessage(), e);
                }
            } else {
                logger.info("Keystore verified at: {}", keystorePath);
                try {
                    ensureKeystoreInClasspath(keystorePath);
                } catch (Exception e) {
                    logger.error("Failed to copy keystore to classpath: {}", e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            logger.error("Error checking keystore: {}", e.getMessage(), e);
        }
    }
} 