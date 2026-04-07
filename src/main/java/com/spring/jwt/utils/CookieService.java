package com.spring.jwt.utils;

import com.spring.jwt.jwt.JwtConfig;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Optional;

@Service
public class CookieService {
    private final JwtConfig jwtConfig;
    
    public static final String REFRESH_TOKEN_COOKIE_NAME = "refresh_token";
    public static final String ACCESS_TOKEN_COOKIE_NAME = "access_token";
    
    @Autowired
    public CookieService(JwtConfig jwtConfig) {
        this.jwtConfig = jwtConfig;
    }
    
    /**
     * secure HttpOnly cookie for the refresh token
     */
    public Cookie createRefreshTokenCookie(String refreshToken) {
        Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE_NAME, refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(jwtConfig.getRefreshExpiration());
        cookie.setAttribute("SameSite", "Strict");
        return cookie;
    }
    
    /**
     * Creates a cookie for the access token (accessible to JavaScript)
     */
    public Cookie createAccessTokenCookie(String accessToken) {
        Cookie cookie = new Cookie(ACCESS_TOKEN_COOKIE_NAME, accessToken);
        cookie.setHttpOnly(false);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge((int) jwtConfig.getExpiration());
        return cookie;
    }
    
    /**
     * Invalidates a cookie by setting its value to empty and max age to 0
     */
    public Cookie invalidateCookie(String name, String path) {
        Cookie cookie = new Cookie(name, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setMaxAge(0);
        cookie.setPath(path != null ? path : "/");
        cookie.setAttribute("SameSite", "Strict");
        return cookie;
    }
    
    /**
     * Extracts the refresh token from cookies
     */
    public String getRefreshTokenFromCookies(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            Optional<Cookie> refreshTokenCookie = Arrays.stream(cookies)
                .filter(cookie -> REFRESH_TOKEN_COOKIE_NAME.equals(cookie.getName()))
                .findFirst();
            
            if (refreshTokenCookie.isPresent()) {
                return refreshTokenCookie.get().getValue();
            }
        }
        return null;
    }
    
    /**
     * Extracts the access token from cookies
     */
    public String getAccessTokenFromCookies(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            Optional<Cookie> accessTokenCookie = Arrays.stream(cookies)
                .filter(cookie -> ACCESS_TOKEN_COOKIE_NAME.equals(cookie.getName()))
                .findFirst();
            
            if (accessTokenCookie.isPresent()) {
                return accessTokenCookie.get().getValue();
            }
        }
        return null;
    }
} 