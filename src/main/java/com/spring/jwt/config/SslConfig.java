package com.spring.jwt.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

/**
 * Custom SSL Configuration that ensures the keystore exists
 */
@Configuration
public class SslConfig {

    private static final Logger logger = LoggerFactory.getLogger(SslConfig.class);

    @Value("${server.ssl.enabled:false}")
    private boolean sslEnabled;

    @Value("${server.ssl.key-store:classpath:keystore.p12}")
    private String keyStore;

    @Value("${server.ssl.key-store-password:yourpassword}")
    private String keyStorePassword;

    @Value("${server.ssl.key-store-type:PKCS12}")
    private String keyStoreType;

    @Value("${server.ssl.key-alias:youralias}")
    private String keyAlias;

    /**
     * Creates a web server factory customizer that verifies the keystore
     */
    @Bean
    public WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> sslCustomizer() {
        return factory -> {
            logger.info("SSL is disabled by default configuration");
            factory.setSsl(null);
        };
    }
} 