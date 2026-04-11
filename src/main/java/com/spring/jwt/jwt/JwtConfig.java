package com.spring.jwt.jwt;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Data
@Component
public class JwtConfig {

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

    @Value("${jwt.refresh-expiration:#{7*24*60*60*10}}")
    private int refreshExpiration;

    @Value("${jwt.not-before:#{1}}")
    private int notBefore;

	@Value("${jwt.allowed-clock-skew-seconds:5}")
	private int allowedClockSkewSeconds;

    @Value("${jwt.secret:3979244226452948404D6251655468576D5A7134743777217A25432A462D4A61}")
    private String secret;

    @Value("${jwt.issuer:Ashutosh}")
    private String issuer;

    @Value("${jwt.audience:Ashutosh-client Side}")
    private String audience;

    @Value("${jwt.device-fingerprinting-enabled:true}")
    private boolean deviceFingerprintingEnabled;

    @Value("${jwt.max-active-sessions:5}")
    private int maxActiveSessions;
}
