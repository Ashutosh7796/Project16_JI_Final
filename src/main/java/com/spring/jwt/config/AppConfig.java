package com.spring.jwt.config;

import com.spring.jwt.config.filter.*;
import com.spring.jwt.jwt.JwtConfig;
import com.spring.jwt.jwt.JwtService;
import com.spring.jwt.repository.UserRepository;
import com.spring.jwt.service.security.UserDetailsServiceCustom;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.ForwardedHeaderFilter;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.spring.jwt.exception.SecurityExceptionHandler;
import com.spring.jwt.utils.SecurityAuditLogger;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(
        securedEnabled = true,
        jsr250Enabled = true
)
@Slf4j
public class AppConfig {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    @Lazy
    private JwtService jwtService;

    @Autowired
    private JwtConfig jwtConfig;

    @Autowired
    private CustomAuthenticationProvider customAuthenticationProvider;

    @Autowired
    private SecurityHeadersFilter securityHeadersFilter;

    @Autowired
    private XssFilter xssFilter;

    @Autowired
    private SqlInjectionFilter sqlInjectionFilter;

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    @Autowired
    private com.spring.jwt.jwt.ActiveSessionService activeSessionService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SecurityAuditLogger securityAuditLogger;

    @Value("${app.url.frontend:http://localhost:5173}")
    private String frontendUrl;

    @Value("#{'${app.cors.allowed-origins}'.split(',')}")
    private List<String> allowedOrigins;

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsServiceCustom userDetailsService() {
        return new UserDetailsServiceCustom(userRepository);
    }

    @Bean
    public JwtRefreshTokenFilter jwtRefreshTokenFilter(
            AuthenticationManager authenticationManager,
            JwtConfig jwtConfig,
            JwtService jwtService,
            UserDetailsServiceCustom userDetailsService,
            com.spring.jwt.jwt.ActiveSessionService activeSessionService) {
        return new JwtRefreshTokenFilter(authenticationManager, jwtConfig, jwtService, userDetailsService, activeSessionService);
    }

    @Bean
    public ForwardedHeaderFilter forwardedHeaderFilter() {
        return new ForwardedHeaderFilter();
    }


    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        log.debug("Configuring security filter chain");
        AuthenticationManager authManager = authenticationManager(http);
        http.csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .ignoringRequestMatchers(
                        "/api/**",
                        "/user/**",
                        "/api/users/**",

                        jwtConfig.getUrl(),
                        jwtConfig.getRefreshUrl()
                )
        );

        http.cors(Customizer.withDefaults());

        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        http.headers(headers -> headers
                .xssProtection(xss -> xss
                        .headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                .contentSecurityPolicy(csp -> csp
                        .policyDirectives("default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'"))
                .frameOptions(frame -> frame.deny())
                .referrerPolicy(referrer -> referrer
                        .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .permissionsPolicy(permissions -> permissions
                        .policy("camera=(), microphone=(), geolocation=()"))
        );

        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // Error endpoint - must be accessible
                .requestMatchers("/error").permitAll()

                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/**").hasRole("ADMIN")

                // Authentication endpoints
                .requestMatchers(jwtConfig.getUrl()).permitAll()
                .requestMatchers(jwtConfig.getRefreshUrl()).permitAll()
                .requestMatchers("/api/auth/v1/register/**").permitAll()
                .requestMatchers("/api/v1/users/password/**").permitAll()
                .requestMatchers("/api/users/**").permitAll()

                // Swagger/OpenAPI endpoints
                .requestMatchers(
                        "/v2/api-docs",
                        "/v3/api-docs",
                        "/v*/a*-docs/**",
                        "/swagger-resources",
                        "/swagger-resources/**",
                        "/configuration/ui",
                        "/configuration/security",
                        "/swagger-ui/**",
                        "/webjars/**",
                        "/swagger-ui.html"
                ).permitAll()

                // Admin dashboard stats — managers may view KPIs (must be before broader /api/v1/admin/**)
                .requestMatchers("/api/v1/admin/users/dashboard/stats").hasAnyRole("ADMIN", "MANAGER")
                
                // Admin attendance endpoints - managers can view employee attendance
                .requestMatchers("/api/v1/admin/attendance/**").hasAnyRole("ADMIN", "MANAGER")
                
                // Admin users endpoints - managers can view employee details
                .requestMatchers("/api/v1/admin/users/employees/**").hasAnyRole("ADMIN", "MANAGER")

                .requestMatchers("/api/v1/admin/employees/**").hasAnyRole("ADMIN", "MANAGER")

                // Other admin endpoints
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

                // Attendance endpoints - must come before general /api/v1/** rules
                .requestMatchers("/api/v1/attendance/**").authenticated()

                // Documents endpoints - must come before general /api/v1/** rules
                .requestMatchers("/api/v1/documents/uploadByUser").permitAll()
                .requestMatchers("/api/v1/documents/**").hasAnyRole("ADMIN", "MANAGER")

                // Interests endpoints - must come before general /api/v1/** rules
                .requestMatchers("/api/v1/interests/**").authenticated()

                // Public endpoints
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/user/**").permitAll()
                .requestMatchers("/api/v1/survey/**").permitAll()
                .requestMatchers("/api/v1/farmer-selfie/**").permitAll()
                .requestMatchers(("/api/v1/farmer-form/**")).permitAll()
                .requestMatchers("/api/v1/exam/**").permitAll()
                .requestMatchers("/api/completeProfile/getProfile/**").permitAll()
                .requestMatchers("/api/v1/complete-profile/public/**").permitAll()
                .requestMatchers("/api/v1/employeeFarmerSurveys/**").permitAll()
                .requestMatchers("/api/v1/farmer_selfie_Survey/**").permitAll()
                .requestMatchers("/api/v1/lab_report/**").permitAll()
                .requestMatchers("/api/v1/products/**").permitAll()
                .requestMatchers("/api/v1/company-weekly-off/**").permitAll()
                .requestMatchers("/api/v1/product-photo/**").permitAll()
                .requestMatchers("/api/v1/employees/**").permitAll()
                .requestMatchers("/api/v1/emp-documents").permitAll()
                .requestMatchers("/api/v1/farmer-payment/**").authenticated()
                .requestMatchers("/api/customer/orders/**").authenticated()

                // Catch-all for remaining /api/v1/** endpoints
                .requestMatchers("/api/v1/**").permitAll()
                .requestMatchers("/api/payment/queue/**").permitAll()
                .requestMatchers("/api/payment/product/**").permitAll()
                .requestMatchers("/api/payment/farmer/**").permitAll()


                .anyRequest().authenticated());

        log.debug("Configuring security filters");
        JwtTokenAuthenticationFilter jwtTokenFilter =
                new JwtTokenAuthenticationFilter(
                        jwtConfig,
                        jwtService,
                        userDetailsService(),
                        activeSessionService
                );

        JwtUsernamePasswordAuthenticationFilter loginFilter =
                new JwtUsernamePasswordAuthenticationFilter(
                        authManager,
                        jwtConfig,
                        jwtService,
                        userRepository,
                        activeSessionService,
                        securityAuditLogger
                );

        JwtRefreshTokenFilter refreshTokenFilter =
                new JwtRefreshTokenFilter(
                        authManager,
                        jwtConfig,
                        jwtService,
                        userDetailsService(),
                        activeSessionService
                );

        http.addFilterBefore(jwtTokenFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterAt(loginFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterAfter(refreshTokenFilter, JwtUsernamePasswordAuthenticationFilter.class);

        http.addFilterBefore(rateLimitingFilter, JwtTokenAuthenticationFilter.class);
        http.addFilterBefore(xssFilter, JwtTokenAuthenticationFilter.class);
        http.addFilterBefore(sqlInjectionFilter, JwtTokenAuthenticationFilter.class);
        http.addFilterBefore(securityHeadersFilter, JwtTokenAuthenticationFilter.class);
        log.debug("Security configuration completed");
        return http.build();
    }

    @Bean
    public SecurityExceptionHandler securityExceptionHandler() {
        return new SecurityExceptionHandler(objectMapper);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        log.info("CORS configured - Allowed Origins: {}", allowedOrigins);

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedHeaders(Arrays.asList("*"));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowCredentials(true);
        config.setExposedHeaders(Arrays.asList("Authorization"));
        config.setMaxAge(3600L);

        return (HttpServletRequest request) -> config;
    }

    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder builder = http.getSharedObject(AuthenticationManagerBuilder.class);
        builder.userDetailsService(userDetailsService())
                .passwordEncoder(passwordEncoder());
        return builder.build();
    }


}
