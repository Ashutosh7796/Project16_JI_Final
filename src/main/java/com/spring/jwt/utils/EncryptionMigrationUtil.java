package com.spring.jwt.utils;

import com.spring.jwt.entity.User;
import com.spring.jwt.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Utility to migrate existing data to encrypted format
 * Only runs when migration profile is active
 */
@Component
@Slf4j
@RequiredArgsConstructor
@Profile("migration")
public class EncryptionMigrationUtil implements ApplicationRunner {

    private final UserRepository userRepository;
    private final EncryptionUtil encryptionUtil;
    
    private static final Pattern BASE64_PATTERN = Pattern.compile("^[A-Za-z0-9+/=]+$");
    
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("Starting encryption migration for user data...");
        
        List<User> users = userRepository.findAll();
        int migrated = 0;
        
        for (User user : users) {
            boolean updated = false;

//            if (user.getFirstName() != null && !isLikelyEncrypted(user.getFirstName())) {
//                user.setFirstName(encryptionUtil.encrypt(user.getFirstName()));
//                updated = true;
//            }
//
//            if (user.getLastName() != null && !isLikelyEncrypted(user.getLastName())) {
//                user.setLastName(encryptionUtil.encrypt(user.getLastName()));
//                updated = true;
//            }
//
//            if (user.getAddress() != null && !isLikelyEncrypted(user.getAddress())) {
//                user.setAddress(encryptionUtil.encrypt(user.getAddress()));
//                updated = true;
//            }
            
            if (updated) {
                userRepository.save(user);
                migrated++;
            }
        }
        
        log.info("Encryption migration completed. Migrated {} users.", migrated);
    }
    
    /**
     * Check if a string is likely to be already encrypted (Base64 encoded)
     */
    private boolean isLikelyEncrypted(String data) {
        if (data.length() < 10) {
            return false;
        }
        
        return BASE64_PATTERN.matcher(data).matches();
    }
} 