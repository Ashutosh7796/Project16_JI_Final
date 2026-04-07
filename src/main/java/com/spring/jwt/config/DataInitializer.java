package com.spring.jwt.config;

import com.spring.jwt.entity.Product;
import com.spring.jwt.entity.Role;
import com.spring.jwt.repository.RoleRepository;
import com.spring.jwt.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@Configuration
@RequiredArgsConstructor
@Slf4j
public class DataInitializer {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
//    private final ProductRepository productRepository;

    private final PasswordEncoder passwordEncoder;



    @Bean
    public CommandLineRunner initData() {
        return args -> {
            initRoles();
//            initAdmin();
//            initProducts();
        };
    }

    private void initRoles() {
        createRoleIfNotFound("ADMIN");
        createRoleIfNotFound("SURVEYOR");
        createRoleIfNotFound("LAB_TECHNICIAN");
        createRoleIfNotFound("USER");
    }

    private void createRoleIfNotFound(String roleName) {
        if (roleRepository.findByName(roleName) == null) {
            Role role = new Role();
            role.setName(roleName);
            roleRepository.save(role);
            log.info("Role created: {}", roleName);
        }
    }

}
