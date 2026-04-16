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
import java.util.HexFormat;
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
    /** When true, logs full JWT + decoded header/payload UTF-8 on parse failure only. Never logs signing secret. */
    private final boolean insecureEmergencyJwtTrace;

    @Autowired
    public JwtServiceImpl(@Lazy UserDetailsService userDetailsService,
                          UserRepository userRepository,
                          @Lazy JwtConfig jwtConfig,
                          TokenBlacklistService tokenBlacklistService,
                          ActiveSessionService activeSessionService,
                          @Value("${app.security.jwt.diagnostic-logging:false}") boolean jwtDiagnosticLogging,
                          @Value("${app.security.jwt.insecure-emergency-trace:false}") boolean insecureEmergencyJwtTrace) {
        this.userDetailsService = userDetailsService;
        this.userRepository = userRepository;
        this.jwtConfig = jwtConfig;
        this.tokenBlacklistService = tokenBlacklistService;
        this.activeSessionService = activeSessionService;
        this.jwtDiagnosticLogging = jwtDiagnosticLogging;
        this.insecureEmergencyJwtTrace = insecureEmergencyJwtTrace;
    }

    @PostConstruct
    void validateJwtSecret() {
        try {
            getKey();
            byte[] rawKeyBytes = Decoders.BASE64.decode(jwtConfig.getSecret());
            byte[] fp = MessageDigest.getInstance("SHA-256").digest(rawKeyBytes);
            String fp12 = HexFormat.of().formatHex(fp).substring(0, 12);
            log.info(
                    "JWT signing key loaded (Base64 decode OK). [jwt-signing] keySha256First12={} issuer={} audience={} accessTtlMs={} refreshTtlSec={} clockSkewSec={} dfpEnabled={} — compare keySha256First12 on every instance; mismatch invalidates all tokens.",
                    fp12,
                    asciiSafeLog(jwtConfig.getIssuer(), 80),
                    asciiSafeLog(jwtConfig.getAudience(), 80),
                    jwtConfig.getExpiration(),
                    jwtConfig.getRefreshExpiration(),
                    jwtConfig.getAllowedClockSkewSeconds(),
                    jwtConfig.isDeviceFingerprintingEnabled());
            if (insecureEmergencyJwtTrace) {
                log.error("app.security.jwt.insecure-emergency-trace=true: on JWT parse failure the server will log the FULL compact token and raw header/payload segments. "
                        + "Signing secret is NEVER logged. Disable immediately after triage.");
            }
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

            String accessJti = UUID.randomUUID().toString();
            JwtBuilder jwtBuilder = Jwts.builder()
                .setSubject(userDetailsCustom.getUsername())
                .setIssuer(jwtConfig.getIssuer())
                .setAudience(jwtConfig.getAudience())
                .setId(accessJti)
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

        String compact = jwtBuilder.compact();
        long expSec = now.plusMillis(jwtConfig.getExpiration()).getEpochSecond();
        jwtDiag("signed access userMasked={} jti={} expEpochSec={} tokenLen={} dfpEmbedded={}",
                maskEmail(userDetailsCustom.getUsername()),
                jtiPrefix(accessJti),
                expSec,
                compact.length(),
                jwtConfig.isDeviceFingerprintingEnabled() && StringUtils.hasText(deviceFingerprint));
        return compact;
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

            String refreshJti = UUID.randomUUID().toString();
            JwtBuilder jwtBuilder = Jwts.builder()
                .setSubject(userDetailsCustom.getUsername())
                .setIssuer(jwtConfig.getIssuer())
                .setId(refreshJti)
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

        String compact = jwtBuilder.compact();
        long expSec = now.plusSeconds(jwtConfig.getRefreshExpiration()).getEpochSecond();
        jwtDiag("signed refresh userMasked={} jti={} expEpochSec={} tokenLen={} dfpEmbedded={}",
                maskEmail(userDetailsCustom.getUsername()),
                jtiPrefix(refreshJti),
                expSec,
                compact.length(),
                jwtConfig.isDeviceFingerprintingEnabled() && StringUtils.hasText(deviceFingerprint));
        return compact;
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
                    asciiSafeLog(tokenParseDetailForLogs(e), 400));
            jwtDiag("parse failure: {}", asciiSafeLog(tokenParseDetailForLogs(e), 400));
            if (insecureEmergencyJwtTrace && StringUtils.hasText(token)) {
                log.error("======== JWT_INSECURE_EMERGENCY_TRACE parse_failed (signing secret NOT logged) ========");
                log.error("JWT_INSECURE_EMERGENCY_TRACE compact_token={}", token);
                log.error("JWT_INSECURE_EMERGENCY_TRACE header_segment_decoded_utf8={}", jwtSegmentDecodedUtf8(token, 0));
                log.error("JWT_INSECURE_EMERGENCY_TRACE payload_segment_decoded_utf8={}", jwtSegmentDecodedUtf8(token, 1));
                log.error("JWT_INSECURE_EMERGENCY_TRACE exception_chain={}", asciiSafeLog(tokenParseDetailForLogs(e), 800));
                log.error("======== END JWT_INSECURE_EMERGENCY_TRACE ========");
            }
            return false;
        }

        try {
            jwtDiag("validate: parsed tokenType={} subjectMasked={} jti={} expEpochSec={} hasDfpClaim={}",
                    claims.get(CLAIM_KEY_TOKEN_TYPE, String.class),
                    maskEmail(claims.getSubject()),
                    jtiPrefix(claims.getId()),
                    claims.getExpiration() != null ? claims.getExpiration().getTime() / 1000L : -1L,
                    StringUtils.hasText(claims.get(CLAIM_KEY_DEVICE_FINGERPRINT, String.class)));

            String tokenId = claims.getId();
            if (tokenId != null && tokenBlacklistService.isBlacklisted(tokenId)) {
                log.warn("[jwt-reject] phase=blacklist jtiPrefix={}", jtiPrefix(tokenId));
                jwtDiag("validate: rejected blacklist jti={}", jtiPrefix(tokenId));
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
                    jwtDiag("validate: rejected dfp tokenPrefix={} requestPrefix={}",
                            tokenDeviceFingerprint.length() >= 8 ? tokenDeviceFingerprint.substring(0, 8) + "..." : tokenDeviceFingerprint,
                            deviceFingerprint.length() >= 8 ? deviceFingerprint.substring(0, 8) + "..." : deviceFingerprint);
                    return false;
                }
            }
            
            try {
                String tokenType = claims.get(CLAIM_KEY_TOKEN_TYPE, String.class);
                
                if (StringUtils.hasText(tokenId)) {
                    if (TOKEN_TYPE_REFRESH.equals(tokenType)) {
                        if (!activeSessionService.isCurrentRefreshToken(username, tokenId)) {
                            log.warn("Refresh token is not current for user: {}", username);
                            jwtDiag("validate: rejected session tokenType=refresh subjectMasked={} jti={}",
                                    maskEmail(username), jtiPrefix(tokenId));
                            return false;
                        }
                    } else {
                        if (!activeSessionService.isCurrentAccessToken(username, tokenId)) {
                            log.warn("Access token is not current for user: {}", username);
                            jwtDiag("validate: rejected session tokenType=access subjectMasked={} jti={}",
                                    maskEmail(username), jtiPrefix(tokenId));
                            return false;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Could not verify active session: {}", e.getMessage());
            }

            jwtDiag("Token validation successful for user={}", maskEmail(username));
            return true;
        } catch (Exception e) {
            log.warn("[jwt-reject] phase=post_claims exType={} msg={}",
                    e.getClass().getSimpleName(),
                    asciiSafeLog(e.getMessage(), 240));
            jwtDiag("post-claims failure: {}", asciiSafeLog(e.toString(), 400));
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
        return jti.length() <= 8 ? jti : jti.substring(0, 8) + "...";
    }

    /**
     * Decode JWT segment (0=header, 1=payload) as UTF-8 for emergency trace only. Does not verify signature.
     */
    private static String jwtSegmentDecodedUtf8(String jwt, int segmentIndex) {
        try {
            String[] parts = jwt.split("\\.");
            if (segmentIndex < 0 || segmentIndex >= parts.length) {
                return "(no segment " + segmentIndex + ")";
            }
            byte[] raw = Decoders.BASE64URL.decode(parts[segmentIndex]);
            return new String(raw, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return "(segment decode failed: " + ex.getMessage() + ")";
        }
    }

    /**
     * Full JWT parse failure text for logs (includes {@link BaseException} outcome + cause chain e.g. MalformedJwtException).
     */
    private static String tokenParseDetailForLogs(Throwable e) {
        if (e instanceof BaseException be) {
            StringBuilder sb = new StringBuilder(be.getOutcomeDescription());
            Throwable c = be.getCause();
            int depth = 0;
            while (c != null && depth < 5) {
                sb.append(" <- ").append(c.getClass().getSimpleName());
                if (StringUtils.hasText(c.getMessage())) {
                    sb.append(":").append(c.getMessage());
                }
                c = c.getCause();
                depth++;
            }
            return sb.toString();
        }
        if (e.getMessage() != null) {
            return e.getMessage();
        }
        return e.toString();
    }

    /**
     * Printable ASCII, single line — avoids journald/syslog treating log fields as binary blobs.
     */
    private static String asciiSafeLog(String s, int maxLen) {
        if (!StringUtils.hasText(s)) {
            return "";
        }
        int n = Math.min(s.length(), maxLen);
        StringBuilder b = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t') {
                b.append(' ');
            } else if (c >= 32 && c <= 126) {
                b.append(c);
            } else {
                b.append('_');
            }
        }
        return b.toString();
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
        } catch (ExpiredJwtException e) {
            throw new BaseException(String.valueOf(HttpStatus.UNAUTHORIZED.value()), "Token expiration", e);
        } catch (UnsupportedJwtException e) {
            throw new BaseException(String.valueOf(HttpStatus.UNAUTHORIZED.value()), "Token's not supported", e);
        } catch (MalformedJwtException e) {
            // Three dot-separated segments does not guarantee valid Base64URL / JSON; do not imply "wrong part count".
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw new BaseException(String.valueOf(HttpStatus.UNAUTHORIZED.value()), "Malformed JWT: " + detail, e);
        } catch (SignatureException e) {
            throw new BaseException(String.valueOf(HttpStatus.UNAUTHORIZED.value()), "Invalid JWT signature", e);
        } catch (Exception e) {
            throw new BaseException(String.valueOf(HttpStatus.UNAUTHORIZED.value()),
                    e.getLocalizedMessage() != null ? e.getLocalizedMessage() : e.getClass().getSimpleName(), e);
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

    @Override
    public void logIssuedPairDiagnostics(String event, String subjectUsername, String accessToken, String refreshToken) {
        if (!jwtDiagnosticLogging) {
            return;
        }
        try {
            Claims a = extractClaims(accessToken);
            Claims r = extractClaims(refreshToken);
            String issState = Objects.equals(jwtConfig.getIssuer(), a.getIssuer()) ? "iss_match" : "iss_mismatch_cfg_vs_token";
            log.info(
                    "[jwt-diag] issued event={} subjectMasked={} accessJti={} refreshJti={} accessExpEpochSec={} refreshExpEpochSec={} accessLen={} refreshLen={} issState={}",
                    asciiSafeLog(event, 60),
                    maskEmail(subjectUsername),
                    jtiPrefix(a.getId()),
                    jtiPrefix(r.getId()),
                    a.getExpiration() != null ? a.getExpiration().getTime() / 1000L : -1L,
                    r.getExpiration() != null ? r.getExpiration().getTime() / 1000L : -1L,
                    accessToken.length(),
                    refreshToken.length(),
                    issState);
        } catch (Exception e) {
            log.warn("[jwt-diag] issued metadata parse failed event={} msg={}",
                    asciiSafeLog(event, 60), asciiSafeLog(e.getMessage(), 200));
        }
    }

    @Override
    public void logInboundTokenDiagnostics(HttpServletRequest request, String token, String hint) {
        if (!jwtDiagnosticLogging) {
            return;
        }
        String reqDfp = generateDeviceFingerprint(request);
        String reqDfpP = StringUtils.hasText(reqDfp) && reqDfp.length() >= 8
                ? reqDfp.substring(0, 8) + "..." : "(none)";
        if (!StringUtils.hasText(token)) {
            log.info("[jwt-diag] inbound hint={} tokenEmpty=true requestDfpPrefix={} hasUserAgent={}",
                    asciiSafeLog(hint, 160), reqDfpP, request.getHeader("User-Agent") != null);
            return;
        }
        Claims c = extractClaimsIgnoreExpiration(token);
        if (c == null) {
            log.info("[jwt-diag] inbound hint={} tokenLen={} parts={} claimsDecodeSkipped requestDfpPrefix={}",
                    asciiSafeLog(hint, 160), token.length(), tokenPartCount(token), reqDfpP);
            return;
        }
        String tokenDfp = c.get(CLAIM_KEY_DEVICE_FINGERPRINT, String.class);
        String tokenDfpP = StringUtils.hasText(tokenDfp) && tokenDfp.length() >= 8
                ? tokenDfp.substring(0, 8) + "..." : "(none)";
        String issState = Objects.equals(jwtConfig.getIssuer(), c.getIssuer()) ? "iss_match" : "iss_mismatch_cfg_vs_token";
        boolean dfpComparable = StringUtils.hasText(tokenDfp) && StringUtils.hasText(reqDfp);
        log.info(
                "[jwt-diag] inbound hint={} tokenLen={} parts={} subjectMasked={} jti={} typ={} expEpochSec={} tokenDfpPrefix={} reqDfpPrefix={} dfpEqual={} issState={} xffPrefix={}",
                asciiSafeLog(hint, 160),
                token.length(),
                tokenPartCount(token),
                maskEmail(c.getSubject()),
                jtiPrefix(c.getId()),
                c.get(CLAIM_KEY_TOKEN_TYPE, String.class),
                c.getExpiration() != null ? c.getExpiration().getTime() / 1000L : -1L,
                tokenDfpP,
                reqDfpP,
                !dfpComparable ? "n/a" : Boolean.toString(tokenDfp.equals(reqDfp)),
                issState,
                asciiSafeLog(request.getHeader("X-Forwarded-For"), 80));
    }
}


