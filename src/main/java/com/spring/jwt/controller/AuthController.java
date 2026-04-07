package com.spring.jwt.controller;

import com.spring.jwt.dto.LoginRequest;
import com.spring.jwt.entity.User;
import com.spring.jwt.exception.BaseException;
import com.spring.jwt.jwt.ActiveSessionService;
import com.spring.jwt.jwt.JwtConfig;
import com.spring.jwt.jwt.JwtService;
import com.spring.jwt.repository.UserRepository;
import com.spring.jwt.service.security.UserDetailsCustom;
import com.spring.jwt.utils.BaseResponseDTO;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final JwtConfig jwtConfig;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final ActiveSessionService activeSessionService;
    private final UserRepository userRepository;

    private static final String REFRESH_TOKEN_COOKIE_NAME = "refresh_token";

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest, HttpServletRequest request,
            HttpServletResponse response) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getUsername(),
                            loginRequest.getPassword()));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            UserDetailsCustom userDetails = (UserDetailsCustom) authentication.getPrincipal();

            String deviceFingerprint = jwtService.generateDeviceFingerprint(request);
            updateUserLoginStats(userDetails.getUsername(), deviceFingerprint);

            String accessToken = jwtService.generateToken(userDetails, deviceFingerprint);
            String refreshToken = jwtService.generateRefreshToken(userDetails, deviceFingerprint);

            registerResultingSession(accessToken, refreshToken, userDetails.getUsername());

            Cookie refreshTokenCookie = createRefreshTokenCookie(refreshToken);
            response.addCookie(refreshTokenCookie);

            Cookie accessTokenCookie = new Cookie("access_token", accessToken);
            accessTokenCookie.setHttpOnly(false);
            accessTokenCookie.setSecure(true);
            accessTokenCookie.setPath("/");
            accessTokenCookie.setMaxAge((int) (jwtConfig.getExpiration() / 1000));
            response.addCookie(accessTokenCookie);

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("accessToken", accessToken);
            responseData.put("tokenType", "Bearer");
            responseData.put("expiresIn", jwtConfig.getExpiration());
            responseData.put("userId", userDetails.getUserId());

            List<String> roles = userDetails.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList());
            responseData.put("roles", roles);

            return ResponseEntity.ok(responseData);

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new BaseResponseDTO(String.valueOf(HttpStatus.UNAUTHORIZED.value()),
                            "Invalid username or password", null));
        } catch (BaseException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new BaseResponseDTO(e.getCode(), e.getMessage(), null));
        } catch (Exception e) {
            log.error("Login error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponseDTO(String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()),
                            "An error occurred during login", null));
        }
    }

    private void updateUserLoginStats(String email, String deviceFingerprint) {
        try {
            User user = userRepository.findByEmail(email);
            if (user != null) {
                user.setLastLogin(LocalDateTime.now());
                user.setDeviceFingerprint(deviceFingerprint);
                user.setFailedLoginAttempts(0);
                user.setAccountLocked(false);
                user.setAccountLockedUntil(null);
                userRepository.save(user);
            }
        } catch (Exception e) {
            log.error("Error updating user stats: {}", e.getMessage());
        }
    }

    private void registerResultingSession(String accessToken, String refreshToken, String username) {
        String accessJti = jwtService.extractTokenId(accessToken);
        String refreshJti = jwtService.extractTokenId(refreshToken);
        activeSessionService.replaceActiveSession(username, accessJti, refreshJti,
                jwtService.extractClaims(accessToken).getExpiration().toInstant(),
                jwtService.extractClaims(refreshToken).getExpiration().toInstant());
    }

    private Cookie createRefreshTokenCookie(String refreshToken) {
        Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE_NAME, refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(jwtConfig.getRefreshExpiration());
        cookie.setAttribute("SameSite", "Strict");
        return cookie;
    }

    @PostMapping("/logout")
    public ResponseEntity<BaseResponseDTO> logout(HttpServletRequest request, HttpServletResponse response) {
        log.info("Processing logout request");

        String username = null;
        String token = request.getHeader("Authorization");
        
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        } else {
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie c : cookies) {
                    if ("access_token".equals(c.getName()) || REFRESH_TOKEN_COOKIE_NAME.equals(c.getName())) {
                        token = c.getValue();
                        break;
                    }
                }
            }
        }
        
        if (token != null) {
            try {
                io.jsonwebtoken.Claims claims = jwtService.extractClaimsIgnoreExpiration(token);
                if (claims != null) {
                    username = claims.getSubject();
                }
            } catch (Exception e) {
                log.warn("Error extracting username during logout: {}", e.getMessage());
            }
        }
        
        if (username != null) {
            activeSessionService.removeActiveSession(username);
        }

        Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE_NAME, "");
        cookie.setMaxAge(0);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        response.addCookie(cookie);

        SecurityContextHolder.clearContext();

        return ResponseEntity.ok(new BaseResponseDTO(String.valueOf(HttpStatus.OK.value()), "Logout successful", null));
    }

//    /**
//     * Test endpoint to check cookies in the request
//     */
//    @GetMapping("/check-cookies")
//    public ResponseEntity<Map<String, Object>> checkCookies(HttpServletRequest request) {
//        Map<String, Object> response = new HashMap<>();
//
//        Cookie[] cookies = request.getCookies();
//        if (cookies != null) {
//            response.put("cookieCount", cookies.length);
//
//            Map<String, String> cookieDetails = Arrays.stream(cookies)
//                    .collect(Collectors.toMap(
//                            Cookie::getName,
//                            cookie -> {
//                                String value = cookie.getValue();
//                                if (value.length() > 10) {
//                                    return value.substring(0, 5) + "..." + value.substring(value.length() - 5);
//                                }
//                                return value;
//                            }));
//
//            response.put("cookies", cookieDetails);
//
//            boolean hasRefreshToken = Arrays.stream(cookies)
//                    .anyMatch(cookie -> REFRESH_TOKEN_COOKIE_NAME.equals(cookie.getName()));
//
//            response.put("hasRefreshToken", hasRefreshToken);
//        } else {
//            response.put("cookieCount", 0);
//            response.put("cookies", Map.of());
//            response.put("hasRefreshToken", false);
//        }
//
//        Map<String, String> headers = new HashMap<>();
//        request.getHeaderNames().asIterator().forEachRemaining(name -> headers.put(name, request.getHeader(name)));
//        response.put("headers", headers);
//
//        return ResponseEntity.ok(response);
//    }
}