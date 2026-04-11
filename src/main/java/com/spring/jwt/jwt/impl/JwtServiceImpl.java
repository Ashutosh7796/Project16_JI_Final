package com.spring.jwt.jwt.impl;

import com.spring.jwt.exception.BaseException;
import com.spring.jwt.jwt.JwtConfig;
import com.spring.jwt.jwt.JwtService;
import com.spring.jwt.jwt.TokenBlacklistService;
import com.spring.jwt.jwt.ActiveSessionService;
import com.spring.jwt.repository.UserRepository;
import com.spring.jwt.service.security.UserDetailsCustom;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class JwtServiceImpl implements JwtService {
    private static final String CLAIM_KEY_DEVICE_FINGERPRINT = "dfp";
    private static final String CLAIM_KEY_TOKEN_TYPE = "token_type";
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";
    private final UserRepository userRepository;
    private final JwtConfig jwtConfig;
    private final UserDetailsService userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;
    private final ActiveSessionService activeSessionService;
    private final boolean jwtDiagnosticLogging;

    @Autowired
    public JwtServiceImpl(@Lazy UserDetailsService userDetailsService,
                          UserRepository userRepository,
                          @Lazy JwtConfig jwtConfig,
                          TokenBlacklistService tokenBlacklistService,
                          ActiveSessionService activeSessionService,
                          @Value("${app.security.jwt.diagnostic-logging:false}") boolean jwtDiagnosticLogging) {
        this.userDetailsService = userDetailsService;
        this.userRepository = userRepository;
        this.jwtConfig = jwtConfig;
        this.tokenBlacklistService = tokenBlacklistService;
        this.activeSessionService = activeSessionService;
        this.jwtDiagnosticLogging = jwtDiagnosticLogging;
    }

    @PostConstruct
    void validateJwtSecret() {
        try {
            getKey();
            log.info("JWT signing key loaded (Base64 decode OK, length validated by JJWT)");
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Invalid jwt.secret: must be a Base64-encoded key (HS256, sufficient length). "
                            + "If you use JWT_SECRET env var, it must be identical on every instance and non-empty.", e);
        }
    }

    @Override
    public Claims extractClaims(String token) {
        return Jwts
                .parserBuilder()
                .setAllowedClockSkewSeconds(jwtConfig.getAllowedClockSkewSeconds())
                .setSigningKey(getKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    @Override
    public Claims extractClaimsIgnoreExpiration(String token) {
        try {
            return Jwts
                .parserBuilder()
                .setAllowedClockSkewSeconds(jwtConfig.getAllowedClockSkewSeconds())
                .setSigningKey(getKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
        } catch (ExpiredJwtException e) {
            return e.getClaims();
        } catch (Exception e) {
            log.warn("Error extracting claims ignoring expiration: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public Key  getKey() {
        byte[] key = Decoders.BASE64.decode(jwtConfig.getSecret());
        return Keys.hmacShaKeyFor(key);
    }

    @Override
    public String generateToken(UserDetailsCustom userDetailsCustom) {
        return generateToken(userDetailsCustom, null);
    }

    @Override
    public String generateToken(UserDetailsCustom userDetailsCustom, String deviceFingerprint) {
        Instant now = Instant.now();
        Instant notBefore = now; // Industrial standard: nbf should not be in the future

        List<String> roles = userDetailsCustom.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        log.debug("Roles: {}", roles);

        Long userId = userDetailsCustom.getUserId();

        try {
            boolean isLocked = userRepository.existsByUserIdAndAccountLockedTrue(Math.toIntExact(userId));

            if (isLocked) {
                throw new BaseException(
                        String.valueOf(HttpStatus.BAD_REQUEST.value()),
                        "connect with admin Your account is lock"
                );
            }
        } catch (BaseException ex) {
            throw ex;
        }
            String firstName = userDetailsCustom.getFirstName();
        
        log.debug("Generating access token for user: {}, device: {}", 
                userDetailsCustom.getUsername(), 
                deviceFingerprint != null ? deviceFingerprint.substring(0, 8) + "..." : "none");

            JwtBuilder jwtBuilder = Jwts.builder()
                .setSubject(userDetailsCustom.getUsername())
                .setIssuer(jwtConfig.getIssuer())
                .setAudience(jwtConfig.getAudience())
                .setId(UUID.randomUUID().toString())
                .claim("firstname", firstName)
                .claim("userId", userId)
                .claim("authorities", roles)
                .claim("isEnable", userDetailsCustom.isEnabled());

        if (userDetailsCustom.getUserProfileId() != null) {
            jwtBuilder.claim("userProfileId", userDetailsCustom.getUserProfileId());
        }

        jwtBuilder.claim(CLAIM_KEY_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                .setIssuedAt(Date.from(now))
                .setNotBefore(Date.from(notBefore))
                .setExpiration(Date.from(now.plusMillis(jwtConfig.getExpiration())))
                .signWith(getKey(), SignatureAlgorithm.HS256);

        if (jwtConfig.isDeviceFingerprintingEnabled() && StringUtils.hasText(deviceFingerprint)) {
            jwtBuilder.claim(CLAIM_KEY_DEVICE_FINGERPRINT, deviceFingerprint);
        }

        return jwtBuilder.compact();
    }
    
    @Override
    public String generateRefreshToken(UserDetailsCustom userDetailsCustom, String deviceFingerprint) {
        Instant now = Instant.now();
        Instant notBefore = now; // Industrial standard: nbf should not be in the future
        
        log.debug("Generating refresh token for user: {}, device: {}", 
                userDetailsCustom.getUsername(), 
                deviceFingerprint != null ? deviceFingerprint.substring(0, 8) + "..." : "none");

        List<String> roles = userDetailsCustom.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

            JwtBuilder jwtBuilder = Jwts.builder()
                .setSubject(userDetailsCustom.getUsername())
                .setIssuer(jwtConfig.getIssuer())
                .setId(UUID.randomUUID().toString())
                .claim("userId", userDetailsCustom.getUserId())
                .claim("authorities", roles);

        if (userDetailsCustom.getUserProfileId() != null) {
            jwtBuilder.claim("userProfileId", userDetailsCustom.getUserProfileId());
        }
        

        
        jwtBuilder.claim(CLAIM_KEY_TOKEN_TYPE, TOKEN_TYPE_REFRESH)
                .setIssuedAt(Date.from(now))
                .setNotBefore(Date.from(notBefore))
                .setExpiration(Date.from(now.plusSeconds(jwtConfig.getRefreshExpiration())))
                .signWith(getKey(), SignatureAlgorithm.HS256);

        if (jwtConfig.isDeviceFingerprintingEnabled() && StringUtils.hasText(deviceFingerprint)) {
            jwtBuilder.claim(CLAIM_KEY_DEVICE_FINGERPRINT, deviceFingerprint);
        }

        return jwtBuilder.compact();
    }
    
    @Override
    public String extractDeviceFingerprint(String token) {
        try {
            Claims claims = extractClaims(token);
            return claims.get(CLAIM_KEY_DEVICE_FINGERPRINT, String.class);
        } catch (Exception e) {
            log.warn("Error extracting device fingerprint from token", e);
            return null;
        }
    }
    
    @Override
    public boolean isRefreshToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            String tokenType = claims.get(CLAIM_KEY_TOKEN_TYPE, String.class);
            log.debug("Token type: {}", tokenType);
            return TOKEN_TYPE_REFRESH.equals(tokenType);
        } catch (Exception e) {
            log.warn("Error checking if token is refresh token: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public String generateDeviceFingerprint(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        
        try {
            String ip = request.getHeader("X-Forwarded-For");
            if (ip != null && ip.contains(",")) {
                ip = ip.split(",")[0].trim();
            }
            if (ip == null || ip.isBlank()) {
                ip = request.getRemoteAddr();
            }
            String ua = request.getHeader("User-Agent");
            String lang = request.getHeader("Accept-Language");
            String enc = request.getHeader("Accept-Encoding");

            StringBuilder deviceInfo = new StringBuilder();
            deviceInfo.append(ua).append("|");
            deviceInfo.append(ip).append("|");
            deviceInfo.append(lang).append("|");
            deviceInfo.append(enc);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(deviceInfo.toString().getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("Error generating device fingerprint", e);
            return null;
        }
    }
    
    @Override
    public Map<String, Object> extractAllCustomClaims(String token) {
        Claims claims = extractClaims(token);

        Map<String, Object> customClaims = new HashMap<>(claims);
        customClaims.remove("sub");
        customClaims.remove("iat");
        customClaims.remove("exp");
        customClaims.remove("jti");
        customClaims.remove("iss");
        customClaims.remove("aud");
        customClaims.remove("nbf");
        
        return customClaims;
    }

    @Override
    public boolean isValidToken(String token) {
        return isValidToken(token, null);
    }
    
    @Override
    public boolean isValidToken(String token, String deviceFingerprint) {
        Claims claims;
        try {
            claims = extractAllClaims(token);
        } catch (Exception e) {
            log.warn("[jwt-reject] phase=parse tokenLen={} parts={} exType={} msg={}",
                    token != null ? token.length() : 0,
                    tokenPartCount(token),
                    e.getClass().getSimpleName(),
                    e.getMessage());
            jwtDiag("parse failure: {}", e.toString());
            return false;
        }

        try {
            String tokenId = claims.getId();
            if (tokenId != null && tokenBlacklistService.isBlacklisted(tokenId)) {
                log.warn("[jwt-reject] phase=blacklist jtiPrefix={}", jtiPrefix(tokenId));
                return false;
            }

            final String username = claims.getSubject();
            
            if (!StringUtils.hasText(username)) {
                log.warn("[jwt-reject] phase=claims subject empty jtiPrefix={}", jtiPrefix(tokenId));
                return false;
            }
    
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            
            if (ObjectUtils.isEmpty(userDetails)) {
                log.warn("[jwt-reject] phase=user_load subject={}", maskEmail(username));
                return false;
            }

            Date nbf = claims.getNotBefore();
            if (nbf != null && nbf.after(new Date())) {
                log.warn("[jwt-reject] phase=nbf notBefore={} now={}", nbf, new Date());
                return false;
            }
            if (jwtConfig.isDeviceFingerprintingEnabled()) {
                String tokenDeviceFingerprint = claims.get(CLAIM_KEY_DEVICE_FINGERPRINT, String.class);
                if (StringUtils.hasText(deviceFingerprint) && StringUtils.hasText(tokenDeviceFingerprint)
                        && !tokenDeviceFingerprint.equals(deviceFingerprint)) {
                    log.warn("Device fingerprint mismatch: token={}, request={}",
                            tokenDeviceFingerprint.substring(0, 8) + "...",
                            deviceFingerprint.substring(0, 8) + "...");
                    return false;
                }
            }
            
            try {
                String tokenType = claims.get(CLAIM_KEY_TOKEN_TYPE, String.class);
                
                if (StringUtils.hasText(tokenId)) {
                    if (TOKEN_TYPE_REFRESH.equals(tokenType)) {
                        if (!activeSessionService.isCurrentRefreshToken(username, tokenId)) {
                            log.warn("Refresh token is not current for user: {}", username);
                            return false;
                        }
                    } else {
                        if (!activeSessionService.isCurrentAccessToken(username, tokenId)) {
                            log.warn("Access token is not current for user: {}", username);
                            return false;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Could not verify active session: {}", e.getMessage());
            }

            jwtDiag("Token validation successful for user={}", username);
            return true;
        } catch (Exception e) {
            log.warn("[jwt-reject] phase=post_claims exType={} msg={}", e.getClass().getSimpleName(), e.getMessage());
            jwtDiag("post-claims failure: {}", e.toString());
            return false;
        }
    }

    private void jwtDiag(String format, Object... args) {
        if (jwtDiagnosticLogging) {
            log.info("[jwt-diag] " + format, args);
        }
    }

    private static int tokenPartCount(String token) {
        if (!StringUtils.hasText(token)) {
            return 0;
        }
        return token.split("\\.").length;
    }

    private static String jtiPrefix(String jti) {
        if (!StringUtils.hasText(jti)) {
            return "(none)";
        }
        return jti.length() <= 8 ? jti : jti.substring(0, 8) + "…";
    }

    private static String maskEmail(String username) {
        if (!StringUtils.hasText(username) || !username.contains("@")) {
            return username != null && username.length() > 2
                    ? username.charAt(0) + "***" + username.charAt(username.length() - 1)
                    : "(short)";
        }
        int at = username.indexOf('@');
        String local = username.substring(0, at);
        String tail = local.length() > 1 ? local.substring(0, 1) + "***" : "*";
        return tail + "@" + username.substring(at + 1);
    }

    private String extractUsername(String token){
        return extractClaims(token, Claims::getSubject);
    }

    private <T> T extractClaims(String token, Function<Claims, T> claimsTFunction){
        final Claims claims = extractAllClaims(token);
        return claimsTFunction.apply(claims);
    }

    private Claims extractAllClaims(String token){
        Claims claims;

        try {
            claims = Jwts.parserBuilder()
                    .setSigningKey(getKey())
                    .setAllowedClockSkewSeconds(jwtConfig.getAllowedClockSkewSeconds())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        }catch (ExpiredJwtException e){
            throw new BaseException(String.valueOf(HttpStatus.UNAUTHORIZED.value()), "Token expiration");
        }catch (UnsupportedJwtException e){
            throw new BaseException(String.valueOf(HttpStatus.UNAUTHORIZED.value()), "Token's not supported");
        }catch (MalformedJwtException e){
            // Three dot-separated segments does not guarantee valid Base64URL / JSON; do not imply "wrong part count".
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw new BaseException(String.valueOf(HttpStatus.UNAUTHORIZED.value()), "Malformed JWT: " + detail);
        }catch (SignatureException e){
            throw new BaseException(String.valueOf(HttpStatus.UNAUTHORIZED.value()), "Invalid JWT signature");
        }catch (Exception e){
            throw new BaseException(String.valueOf(HttpStatus.UNAUTHORIZED.value()), e.getLocalizedMessage());
        }

        return claims;
    }

    @Override
    public void blacklistToken(String token) {
        try {
            Claims claims = extractClaims(token);
            String tokenId = claims.getId();
            Date expiration = claims.getExpiration();
            
            if (tokenId != null && expiration != null) {
                tokenBlacklistService.blacklistToken(tokenId, expiration.toInstant());
                log.debug("Token blacklisted: {}", tokenId);
            }
        } catch (Exception e) {
            log.error("Error blacklisting token: {}", e.getMessage());
        }
    }
    
    @Override
    public String extractTokenId(String token) {
        try {
            Claims claims = extractClaims(token);
            return claims.getId();
        } catch (Exception e) {
            log.warn("Error extracting token ID: {}", e.getMessage());
            return null;
        }
    }
    
    @Override
    public boolean isBlacklisted(String token) {
        try {
            String tokenId = extractTokenId(token);
            return tokenId != null && tokenBlacklistService.isBlacklisted(tokenId);
        } catch (Exception e) {
            log.warn("Error checking blacklist: {}", e.getMessage());
            return false;
        }
    }
}


