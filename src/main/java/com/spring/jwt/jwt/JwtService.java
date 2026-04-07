package com.spring.jwt.jwt;

import com.spring.jwt.service.security.UserDetailsCustom;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;

import java.security.Key;
import java.util.Map;

public interface JwtService {

    Claims extractClaims(String token);

    Claims extractClaimsIgnoreExpiration(String token);

    Key getKey();

    String generateToken(UserDetailsCustom userDetailsCustom);

    String generateToken(UserDetailsCustom userDetailsCustom, String deviceFingerprint);

    String generateRefreshToken(UserDetailsCustom userDetailsCustom, String deviceFingerprint);

    String extractDeviceFingerprint(String token);

    boolean isValidToken(String token, String deviceFingerprint);

    boolean isValidToken(String token);

    boolean isRefreshToken(String token);

    String generateDeviceFingerprint(HttpServletRequest request);

    Map<String, Object> extractAllCustomClaims(String token);
    
    /**
     * Blacklist a token to prevent reuse
     * @param token The token to blacklist
     */
    void blacklistToken(String token);
    
    /**
     * Extract the token ID (jti) from a token
     * @param token The token to extract ID from
     * @return The token ID
     */
    String extractTokenId(String token);
    
    /**
     * Check if a token is blacklisted
     * @param token The token to check
     * @return true if the token is blacklisted
     */
    boolean isBlacklisted(String token);
}
