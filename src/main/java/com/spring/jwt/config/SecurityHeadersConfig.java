package com.spring.jwt.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.header.HeaderWriter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Configuration for enhanced security headers
 */
@Configuration
public class SecurityHeadersConfig {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CSP_NONCE_ATTRIBUTE = "cspNonce";
    
    /**
     * Generates nonce values for CSP
     */
    @Bean
    public OncePerRequestFilter cspNonceFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                           FilterChain filterChain) throws ServletException, IOException {
                byte[] nonceBytes = new byte[16];
                RANDOM.nextBytes(nonceBytes);
                String nonce = Base64.getEncoder().encodeToString(nonceBytes);

                request.setAttribute(CSP_NONCE_ATTRIBUTE, nonce);

                String cspHeader = getContentSecurityPolicy(nonce);
                response.setHeader("Content-Security-Policy", cspHeader);
                
                filterChain.doFilter(request, response);
            }
        };
    }
    
    /**
     * Creates a Content Security Policy with nonce
     */
    private String getContentSecurityPolicy(String nonce) {
        return String.format(
            "default-src 'self'; " +
            "script-src 'self' 'nonce-%s' https://cdnjs.cloudflare.com https://cdn.jsdelivr.net; " +
            "style-src 'self' 'nonce-%s' https://fonts.googleapis.com https://cdn.jsdelivr.net; " +
            "img-src 'self' data:; " +
            "font-src 'self' https://fonts.gstatic.com; " +
            "connect-src 'self'; " +
            "frame-src 'none'; " +
            "object-src 'none'; " +
            "base-uri 'self'; " +
            "form-action 'self'; " +
            "frame-ancestors 'none'; " +
            "upgrade-insecure-requests;",
            nonce, nonce);
    }
    
    /**
     * Headers for API endpoints (no caching allowed)
     */
    @Bean
    public HeaderWriter apiSecurityHeaders() {
        RequestMatcher apiMatcher = new OrRequestMatcher(Arrays.asList(
                new AntPathRequestMatcher("/api/**"),
                new AntPathRequestMatcher("/questions/**")
        ));
        
        return new DelegatingRequestMatcherHeaderWriter(
                apiMatcher,
                new StaticHeadersWriter(
                        "Cache-Control", "no-store, no-cache, must-revalidate, max-age=0",
                        "Pragma", "no-cache",
                        "Expires", "0",
                        "X-Content-Type-Options", "nosniff",
                        "X-Frame-Options", "DENY",
                        "X-XSS-Protection", "1; mode=block",
                        "Strict-Transport-Security", "max-age=31536000; includeSubDomains"
                )
        );
    }
    
    /**
     * Headers for static resources (caching allowed)
     */
    @Bean
    public HeaderWriter staticResourcesHeaders() {
        RequestMatcher staticResourcesMatcher = new OrRequestMatcher(Arrays.asList(
                new AntPathRequestMatcher("/static/**"),
                new AntPathRequestMatcher("/css/**"),
                new AntPathRequestMatcher("/js/**"),
                new AntPathRequestMatcher("/images/**"),
                new AntPathRequestMatcher("/webjars/**"),
                new AntPathRequestMatcher("/**/*.ico")
        ));
        
        return new DelegatingRequestMatcherHeaderWriter(
                staticResourcesMatcher,
                new StaticHeadersWriter(
                        "Cache-Control", "max-age=31536000, public",
                        "X-Content-Type-Options", "nosniff"
                )
        );
    }
    
    /**
     * Creates a referrer policy header writer
     */
    @Bean
    public ReferrerPolicyHeaderWriter referrerPolicyHeaderWriter() {
        return new ReferrerPolicyHeaderWriter(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN);
    }
    
    /**
     * Creates a Permissions-Policy header
     */
    @Bean
    public HeaderWriter permissionsPolicyHeaderWriter() {
        return new StaticHeadersWriter(
                "Permissions-Policy", 
                "camera=(), microphone=(), geolocation=(), payment=(), usb=()");
    }
    
    /**
     * Creates the Feature Policy header
     */
    @Bean
    public HeaderWriter featurePolicyHeaderWriter() {
        return new StaticHeadersWriter(
                "Feature-Policy", 
                "camera 'none'; microphone 'none'; geolocation 'none'; payment 'none'; usb 'none'");
    }
    
    /**
     * Creates a Cross-Origin-Embedder-Policy header
     */
    @Bean
    public HeaderWriter crossOriginEmbedderPolicyHeaderWriter() {
        return new StaticHeadersWriter("Cross-Origin-Embedder-Policy", "require-corp");
    }
    
    /**
     * Creates a Cross-Origin-Opener-Policy header
     */
    @Bean
    public HeaderWriter crossOriginOpenerPolicyHeaderWriter() {
        return new StaticHeadersWriter("Cross-Origin-Opener-Policy", "same-origin");
    }
    
    /**
     * Creates a Cross-Origin-Resource-Policy header
     */
    @Bean
    public HeaderWriter crossOriginResourcePolicyHeaderWriter() {
        return new StaticHeadersWriter("Cross-Origin-Resource-Policy", "same-origin");
    }
} 