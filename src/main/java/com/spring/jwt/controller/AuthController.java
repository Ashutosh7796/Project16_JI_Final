package com.spring.jwt.controller;

import com.spring.jwt.jwt.ActiveSessionService;
import com.spring.jwt.jwt.JwtConfig;
import com.spring.jwt.jwt.JwtService;
import com.spring.jwt.repository.UserRepository;
import com.spring.jwt.utils.BaseResponseDTO;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

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