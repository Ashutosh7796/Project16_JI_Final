package com.spring.jwt.config.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.jwt.dto.RefreshTokenRequest;
import com.spring.jwt.jwt.JwtConfig;
import com.spring.jwt.jwt.JwtService;
import com.spring.jwt.jwt.ActiveSessionService;
import com.spring.jwt.service.security.UserDetailsCustom;
import com.spring.jwt.service.security.UserDetailsServiceCustom;
import com.spring.jwt.utils.BaseResponseDTO;
import com.spring.jwt.utils.HelperUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import java.io.IOException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class JwtRefreshTokenFilter extends AbstractAuthenticationProcessingFilter implements Ordered {

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final UserDetailsServiceCustom userDetailsService;
    private final JwtConfig jwtConfig;
    private final ActiveSessionService activeSessionService;

    private static final String REFRESH_TOKEN_COOKIE_NAME = "refresh_token";
    private static final String CLAIM_KEY_TOKEN_TYPE = "token_type";
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";

    public JwtRefreshTokenFilter(AuthenticationManager manager,
                                JwtConfig jwtConfig,
                                 JwtService jwtService,
                                 UserDetailsServiceCustom userDetailsService,
                                 ActiveSessionService activeSessionService) {
        super(new AntPathRequestMatcher(jwtConfig.getRefreshUrl(), "POST"));
        setAuthenticationManager(manager);
        this.objectMapper = new ObjectMapper();
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.jwtConfig = jwtConfig;
        this.activeSessionService = activeSessionService;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 125;
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
            throws AuthenticationException, IOException, ServletException {
        log.info("Start refresh token authentication");

        String refreshToken = null;
        
        try {
            refreshToken = getRefreshTokenFromCookie(request);
            if (refreshToken != null) {
                log.info("Found refresh token in cookie");
            }

            if (refreshToken == null) {
                try {
                    if (request.getContentLength() > 0) {
                        RefreshTokenRequest refreshRequest = objectMapper.readValue(request.getInputStream(), RefreshTokenRequest.class);
                        refreshToken = refreshRequest.getRefreshToken();
                        log.info("Found refresh token in request body");
                    } else {
                        log.info("Request body is empty");
                    }
                } catch (Exception e) {
                    log.debug("Failed to parse request body: {}", e.getMessage());
                }
            }

            if (refreshToken == null) {
                String authHeader = request.getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    refreshToken = authHeader.substring(7);
                    log.info("Found refresh token in Authorization header");
                }
            }

            if (refreshToken == null) {
                Cookie[] cookies = request.getCookies();
                if (cookies != null) {
                    log.info("Available cookies:");
                    for (Cookie cookie : cookies) {
                        log.info("Cookie: {} = {}, Path: {}, HttpOnly: {}", 
                            cookie.getName(), 
                            cookie.getValue().substring(0, Math.min(10, cookie.getValue().length())) + "...",
                            cookie.getPath(),
                            cookie.isHttpOnly());
                    }
                } else {
                    log.info("No cookies found in request");
                }

                log.info("Available headers:");
                Enumeration<String> headerNames = request.getHeaderNames();
                while (headerNames.hasMoreElements()) {
                    String headerName = headerNames.nextElement();
                    log.info("Header: {} = {}", headerName, request.getHeader(headerName));
                }
                
                throw new BadCredentialsException("No refresh token provided");
            }

            log.info("Validating refresh token...");

            try {
                Claims claims = jwtService.extractClaims(refreshToken);
                String tokenType = claims.get(CLAIM_KEY_TOKEN_TYPE, String.class);
                
                if (TOKEN_TYPE_ACCESS.equals(tokenType)) {
                    log.error("Attempting to use an access token for refresh. Please use a refresh token instead.");
                    Cookie invalidCookie = new Cookie(REFRESH_TOKEN_COOKIE_NAME, "");
                    invalidCookie.setMaxAge(0);
                    invalidCookie.setPath("/");
                    response.addCookie(invalidCookie);
                    throw new BadCredentialsException("Access token used for refresh. Please use a refresh token.");
                } else if (!TOKEN_TYPE_REFRESH.equals(tokenType)) {
                    log.error("Invalid token type - not a refresh token");
                    throw new BadCredentialsException("Invalid token type - not a refresh token");
                }
            } catch (BadCredentialsException e) {
                throw e;
            } catch (Exception e) {
                log.error("Error validating token type: {}", e.getMessage());
                throw new BadCredentialsException("Invalid token format or structure");
            }

            String deviceFingerprint = jwtService.generateDeviceFingerprint(request);
            if (!jwtService.isValidToken(refreshToken, deviceFingerprint)) {
                log.error("Expired or invalid refresh token");
                throw new BadCredentialsException("Expired or invalid refresh token");
            }

            Claims claims = jwtService.extractClaims(refreshToken);
            String username = claims.getSubject();
            log.info("Refresh token is valid for user: {}", username);

            RefreshTokenAuthentication auth = new RefreshTokenAuthentication(username, refreshToken);
            auth.setAuthenticated(true);
            
            return auth;
        } catch (Exception e) {
            log.error("Error processing refresh token: {}", e.getMessage());
            throw new BadCredentialsException(e.getMessage());
        }
    }
    
    /**
     * Get refresh token from cookie
     */
    private String getRefreshTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            Optional<Cookie> refreshTokenCookie = Arrays.stream(cookies)
                .filter(cookie -> REFRESH_TOKEN_COOKIE_NAME.equals(cookie.getName()))
                .findFirst();
            
            if (refreshTokenCookie.isPresent()) {
                log.debug("Found refresh token in cookie");
                String tokenValue = refreshTokenCookie.get().getValue();

                try {
                    if (!jwtService.isRefreshToken(tokenValue)) {
                        log.warn("Cookie named 'refresh_token' contains an access token, not a refresh token");
                    }
                } catch (Exception e) {
                    log.warn("Error checking token type from cookie: {}", e.getMessage());
                }
                
                return tokenValue;
            }
        }
        return null;
    }

    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain,
                                           Authentication authResult) throws IOException, ServletException {
        try {
            log.info("Processing successful refresh token authentication");
            String username = authResult.getName();
            String refreshToken = ((RefreshTokenAuthentication) authResult).getRefreshToken();

            jwtService.blacklistToken(refreshToken);
            log.debug("Blacklisted used refresh token");

            UserDetailsCustom userDetails = (UserDetailsCustom) userDetailsService.loadUserByUsername(username);

            String deviceFingerprint = jwtService.generateDeviceFingerprint(request);

            String newAccessToken = jwtService.generateToken(userDetails, deviceFingerprint);
            String newRefreshToken = jwtService.generateRefreshToken(userDetails, deviceFingerprint);

            String accessJti = jwtService.extractTokenId(newAccessToken);
            String refreshJti = jwtService.extractTokenId(newRefreshToken);
            activeSessionService.replaceActiveSession(username, accessJti, refreshJti,
                    jwtService.extractClaims(newAccessToken).getExpiration().toInstant(),
                    jwtService.extractClaims(newRefreshToken).getExpiration().toInstant());

            Cookie refreshTokenCookie = createRefreshTokenCookie(newRefreshToken);
            response.addCookie(refreshTokenCookie);

            Cookie accessTokenCookie = new Cookie("access_token", newAccessToken);
            accessTokenCookie.setHttpOnly(false);
            accessTokenCookie.setSecure(true);
            accessTokenCookie.setPath("/");
            accessTokenCookie.setMaxAge((int) jwtConfig.getExpiration());
            response.addCookie(accessTokenCookie);

            Map<String, Object> tokens = new HashMap<>();
            tokens.put("accessToken", newAccessToken);
            tokens.put("tokenType", "Bearer");
            tokens.put("expiresIn", jwtConfig.getExpiration());
            
            String json = HelperUtils.JSON_WRITER.writeValueAsString(tokens);
            response.setContentType("application/json; charset=UTF-8");
            response.getWriter().write(json);
            log.info("End successful refresh token authentication");
        } catch (Exception ex) {
            log.error("Error during token refresh: {}", ex.getMessage());
            unsuccessfulAuthentication(request, response, new BadCredentialsException(ex.getMessage()));
        }
    }
    
    /**
     * Create a secure HttpOnly cookie for the refresh token
     */
    private Cookie createRefreshTokenCookie(String refreshToken) {
        Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE_NAME, refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");

        cookie.setMaxAge(jwtConfig.getRefreshExpiration());

        cookie.setAttribute("SameSite", "Strict");
        
        return cookie;
    }

    @Override
    protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response, 
                                             AuthenticationException failed) throws IOException, ServletException {

        Cookie invalidCookie = new Cookie(REFRESH_TOKEN_COOKIE_NAME, "");
        invalidCookie.setMaxAge(0);
        invalidCookie.setPath(jwtConfig.getRefreshUrl());
        response.addCookie(invalidCookie);
        
        BaseResponseDTO responseDTO = new BaseResponseDTO();
        responseDTO.setCode(String.valueOf(HttpStatus.UNAUTHORIZED.value()));
        responseDTO.setMessage(failed.getMessage());

        String json = HelperUtils.JSON_WRITER.writeValueAsString(responseDTO);

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json; charset=UTF-8");
        response.getWriter().write(json);
    }

    public static class RefreshTokenAuthentication implements Authentication {
        private final String username;
        private final String refreshToken;
        private boolean authenticated = false;
        
        public RefreshTokenAuthentication(String username, String refreshToken) {
            this.username = username;
            this.refreshToken = refreshToken;
        }
        
        @Override
        public Object getCredentials() {
            return refreshToken;
        }
        
        @Override
        public Object getPrincipal() {
            return username;
        }
        
        @Override
        public boolean isAuthenticated() {
            return authenticated;
        }
        
        @Override
        public void setAuthenticated(boolean isAuthenticated) {
            this.authenticated = isAuthenticated;
        }
        
        @Override
        public String getName() {
            return username;
        }
        
        public String getRefreshToken() {
            return refreshToken;
        }
        
        @Override
        public Object getDetails() {
            return null;
        }
        
        @Override
        public java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> getAuthorities() {
            return java.util.Collections.emptyList();
        }
    }
} 