package com.spring.jwt.config.filter;

import com.spring.jwt.jwt.ActiveSessionService;
import com.spring.jwt.jwt.JwtConfig;
import com.spring.jwt.jwt.JwtService;
import com.spring.jwt.service.security.UserDetailsServiceCustom;
import com.spring.jwt.utils.BaseResponseDTO;
import com.spring.jwt.utils.HelperUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

@Slf4j
public class JwtTokenAuthenticationFilter extends OncePerRequestFilter {

    private final JwtConfig jwtConfig;
    private final JwtService jwtService;
    private final UserDetailsServiceCustom userDetailsService;
    private final ActiveSessionService activeSessionService;
    private final boolean jwtDiagnosticLogging;

    private static final String ACCESS_TOKEN_COOKIE_NAME = "access_token";

    public JwtTokenAuthenticationFilter(JwtConfig jwtConfig,
                                          JwtService jwtService,
                                          UserDetailsServiceCustom userDetailsService,
                                          ActiveSessionService activeSessionService,
                                          boolean jwtDiagnosticLogging) {
        this.jwtConfig = jwtConfig;
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.activeSessionService = activeSessionService;
        this.jwtDiagnosticLogging = jwtDiagnosticLogging;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = getJwtFromRequest(request);
        if (!StringUtils.hasText(token)) {
            // No credentials: leave context anonymous (permitAll routes still work).
            filterChain.doFilter(request, response);
            return;
        }

        if (jwtDiagnosticLogging) {
            String authHeader = request.getHeader(jwtConfig.getHeader());
            log.info("[jwt-diag] access_token_present method={} servletPath={} requestURI={} authHeaderLen={} hasAccessCookie={}",
                    request.getMethod(),
                    request.getServletPath(),
                    safeUriWithoutQuery(request),
                    authHeader != null ? authHeader.length() : 0,
                    hasAccessTokenCookie(request));
        }

        try {
            if (!jwtService.isValidToken(token)) {
                String reason = getSpecificInvalidReason(token, request);
                if (jwtDiagnosticLogging) {
                    jwtService.logInboundTokenDiagnostics(request, token,
                            "IS_VALID_TOKEN_FALSE; detail=" + reason);
                }
                logAuthFailure(request, token, "IS_VALID_TOKEN_FALSE", reason);
                handleInvalidToken(response, reason);
                return;
            }

            Claims claims = jwtService.extractClaims(token);
            String username = claims.getSubject();
            Integer userId = claims.get("userId", Integer.class);
            String tokenId = claims.getId();

            if (jwtDiagnosticLogging) {
                log.info("[jwt-diag] token_ok subject={} userId={} jtiPrefix={} path={}",
                        maskUser(username),
                        userId,
                        jtiPrefix(tokenId),
                        safeUriWithoutQuery(request));
            }

            if (!activeSessionService.isCurrentAccessToken(username, tokenId)) {
                if (jwtDiagnosticLogging) {
                    jwtService.logInboundTokenDiagnostics(request, token,
                            "ACTIVE_SESSION_MISMATCH after isValidToken_true");
                }
                logAuthFailure(request, token, "ACTIVE_SESSION_MISMATCH",
                        "access jti not current for subject=" + maskUser(username));
                handleInvalidToken(response,
                        "You are logged in on another device. Please logout from the other device to continue");
                return;
            }

            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );

            authentication.setDetails(userId);
            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);

        } catch (ExpiredJwtException ex) {
            SecurityContextHolder.clearContext();
            logAuthFailure(request, token, "EXPIRED_JWT_EXCEPTION", ex.getMessage());
            handleExpiredToken(response);

        } catch (JwtException ex) {
            SecurityContextHolder.clearContext();
            logAuthFailure(request, token, "JWT_EXCEPTION", ex.getClass().getSimpleName() + ": " + ex.getMessage());
            handleInvalidToken(response, "Invalid JWT token");

        } catch (Exception ex) {
            SecurityContextHolder.clearContext();
            logAuthFailure(request, token, "UNEXPECTED", ex.getClass().getSimpleName() + ": " + ex.getMessage());
            handleAuthenticationException(response, ex);
        }
    }

    private static String safeUriWithoutQuery(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return "";
        }
        int q = uri.indexOf('?');
        return q > 0 ? uri.substring(0, q) : uri;
    }

    private static boolean hasAccessTokenCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        return Arrays.stream(cookies).anyMatch(c -> ACCESS_TOKEN_COOKIE_NAME.equals(c.getName()));
    }

    private static int tokenPartCount(String token) {
        if (!StringUtils.hasText(token)) {
            return 0;
        }
        return token.split("\\.").length;
    }

    private static String maskUser(String username) {
        if (!StringUtils.hasText(username)) {
            return "(empty)";
        }
        if (username.length() <= 3) {
            return "***";
        }
        return username.charAt(0) + "***" + username.charAt(username.length() - 1);
    }

    private static String jtiPrefix(String jti) {
        if (!StringUtils.hasText(jti)) {
            return "(none)";
        }
        return jti.length() <= 6 ? jti.substring(0, Math.min(3, jti.length())) + "..." : jti.substring(0, 6) + "...";
    }

    private void logAuthFailure(HttpServletRequest request, String token, String stage, String detail) {
        int len = StringUtils.hasText(token) ? token.length() : 0;
        int parts = tokenPartCount(token);
        String xff = request.getHeader("X-Forwarded-For");
        String xffShort = StringUtils.hasText(xff) && xff.length() > 60 ? xff.substring(0, 60) + "..." : xff;
        log.warn("[auth-fail] stage={} method={} servletPath={} uri={} tokenLen={} tokenParts={} hasAccessCookie={} xForwardedFor={} detail={}",
                stage,
                request.getMethod(),
                request.getServletPath(),
                safeUriWithoutQuery(request),
                len,
                parts,
                hasAccessTokenCookie(request),
                asciiSafeLog(xffShort, 80),
                asciiSafeLog(detail, 300));
    }
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {

        String path = request.getServletPath();

        // Do NOT skip /api/auth/** — e.g. POST /api/auth/v1/register needs JWT applied when an admin
        // registers a MANAGER (permitAll + SecurityContext must reflect Bearer or access_token cookie).
        return path.equals("/jwt/login")
                || path.equals(jwtConfig.getUrl())
                || path.equals(jwtConfig.getRefreshUrl())
                || path.startsWith("/swagger")
                || path.startsWith("/v3/api-docs");
    }

    /**
     * Extract JWT token from header or cookie
     */
    private String getJwtFromRequest(HttpServletRequest request) {

        String bearerToken = request.getHeader(jwtConfig.getHeader());
        if (StringUtils.hasText(bearerToken) &&
                bearerToken.startsWith(jwtConfig.getPrefix() + " ")) {
            return bearerToken.substring((jwtConfig.getPrefix() + " ").length()).trim();
        }

        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            Optional<Cookie> accessTokenCookie = Arrays.stream(cookies)
                    .filter(c -> ACCESS_TOKEN_COOKIE_NAME.equals(c.getName()))
                    .findFirst();

            if (accessTokenCookie.isPresent()) {
                return accessTokenCookie.get().getValue();
            }
        }
        return null;
    }

    /**
     * Detailed invalid token reason
     */
    private String getSpecificInvalidReason(String token, HttpServletRequest request) {
        try {
            Claims claims = jwtService.extractClaims(token);

            String tokenId = claims.getId();
            if (tokenId != null && jwtService.isBlacklisted(token)) {
                return "Token is revoked. Please login again.";
            }

            String tokenDfp = claims.get("dfp", String.class);
            String reqDfp = jwtService.generateDeviceFingerprint(request);

            if (StringUtils.hasText(tokenDfp) &&
                    StringUtils.hasText(reqDfp) &&
                    !tokenDfp.equals(reqDfp)) {
                return "Device mismatch. Please login again.";
            }

            return "Session is no longer active. Please login again.";

        } catch (ExpiredJwtException e) {
            if (jwtDiagnosticLogging) {
                log.info("[jwt-diag] getSpecificInvalidReason: expired exp={}", e.getClaims() != null ? e.getClaims().getExpiration() : "n/a");
            }
            return "Token has expired. Please login again.";
        } catch (SignatureException e) {
            log.warn("[jwt-reject] SignatureException in getSpecificInvalidReason - check jwt.secret / JWT_SECRET parity across pods");
            return "Invalid token signature. Re-login; if this persists, ensure every server instance uses the same jwt.secret (Base64).";
        } catch (JwtException e) {
            log.warn("[jwt-reject] JwtException in getSpecificInvalidReason type={} msg={}",
                    e.getClass().getSimpleName(), asciiSafeLog(e.getMessage(), 240));
            return "Malformed or invalid token. Please clear your browser data and login again.";
        } catch (Exception e) {
            log.warn("[jwt-reject] unexpected in getSpecificInvalidReason type={} msg={}",
                    e.getClass().getSimpleName(), asciiSafeLog(e.getMessage(), 240));
            return "Authentication failed. Please login again.";
        }
    }

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

    private void handleInvalidToken(HttpServletResponse response, String message)
            throws IOException {

        BaseResponseDTO dto = new BaseResponseDTO();
        dto.setCode(String.valueOf(HttpStatus.UNAUTHORIZED.value()));
        dto.setMessage(message);

        writeResponse(response, HttpStatus.UNAUTHORIZED, dto);
    }

    private void handleExpiredToken(HttpServletResponse response)
            throws IOException {

        BaseResponseDTO dto = new BaseResponseDTO();
        dto.setCode(String.valueOf(HttpStatus.UNAUTHORIZED.value()));
        dto.setMessage("Expired token");

        writeResponse(response, HttpStatus.UNAUTHORIZED, dto);
    }

    private void handleAuthenticationException(HttpServletResponse response, Exception e)
            throws IOException {

        BaseResponseDTO dto = new BaseResponseDTO();
        dto.setCode(String.valueOf(HttpStatus.UNAUTHORIZED.value()));
        dto.setMessage("Authentication failed");

        writeResponse(response, HttpStatus.UNAUTHORIZED, dto);
    }

    private void writeResponse(HttpServletResponse response,
                               HttpStatus status,
                               BaseResponseDTO dto) throws IOException {

        response.setStatus(status.value());
        response.setContentType("application/json; charset=UTF-8");
        response.getWriter().write(
                HelperUtils.JSON_WRITER.writeValueAsString(dto)
        );
    }
}
