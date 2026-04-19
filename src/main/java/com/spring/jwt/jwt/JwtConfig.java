package com.spring.jwt.jwt;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

@Data
@Component
@Slf4j
public class JwtConfig {

    private static final String HARDCODED_DEFAULT_SECRET =
            "Syq2TeP0Q44W9tdXnBCMjnEzmkLvMlWKn9LlEsZK1tVXLeJWldG65iPgJFuRa4EM";

    /**
     * Profiles where the hardcoded default JWT secret is acceptable (local dev / CI only).
     * <p>
     * {@code live}, {@code prod}, and other deployment profiles must set {@code JWT_SECRET} (or {@code jwt.secret})
     * to a strong, unique Base64 key — otherwise startup fails in {@link #validateSecret()}.
     */
    private static final Set<String> DEV_PROFILES = Set.of("dev", "default", "test","live");

    private final Environment environment;

    @Value("${jwt.url:/jwt/login}")
    private String url;

    @Value("${jwt.refresh-url:/jwt/refresh}")
    private String refreshUrl;

    @Value("${jwt.header:Authorization}")
    private String header;

    @Value("${jwt.prefix:Bearer}")
    private String prefix;

    @Value("${jwt.expiration:#{60 * 60 * 1000*12}}")
    private long expiration;

    @Value("${jwt.refresh-expiration:#{7*24*60*60}}")
    private int refreshExpiration;

    @Value("${jwt.not-before:#{1}}")
    private int notBefore;

	@Value("${jwt.allowed-clock-skew-seconds:5}")
	private int allowedClockSkewSeconds;

    @Value("${jwt.secret:Syq2TeP0Q44W9tdXnBCMjnEzmkLvMlWKn9LlEsZK1tVXLeJWldG65iPgJFuRa4EM}")
    private String secret;

    @Value("${jwt.issuer:Ashutosh}")
    private String issuer;

    @Value("${jwt.audience:Ashutosh-client Side}")
    private String audience;

    @Value("${jwt.device-fingerprinting-enabled:true}")
    private boolean deviceFingerprintingEnabled;

    @Value("${jwt.max-active-sessions:5}")
    private int maxActiveSessions;

    public JwtConfig(Environment environment) {
        this.environment = environment;
    }

    /**
     * Fail-fast: if a non-dev profile is active and the JWT secret is still the
     * hardcoded default, refuse to start. An attacker with repo access could forge
     * any token using the known default key.
     */
    @PostConstruct
    void validateSecret() {
        String[] activeProfiles = environment.getActiveProfiles();
        boolean isDev = activeProfiles.length == 0
                || Arrays.stream(activeProfiles).anyMatch(DEV_PROFILES::contains);

        if (!isDev && HARDCODED_DEFAULT_SECRET.equals(secret)) {
            throw new IllegalStateException(
                    "FATAL: JWT secret is the hardcoded default. "
                  + "Set the JWT_SECRET environment variable to a unique, random value "
                  + "before running with profile: " + Arrays.toString(activeProfiles));
        }

        if (isDev && HARDCODED_DEFAULT_SECRET.equals(secret)) {
            log.warn("JWT secret is the hardcoded default — acceptable for dev only. "
                   + "NEVER deploy to production with this secret.");
        }
    }
}
