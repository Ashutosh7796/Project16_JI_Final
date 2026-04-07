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
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class JwtTokenAuthenticationFilter extends OncePerRequestFilter {

    private final JwtConfig jwtConfig;
    private final JwtService jwtService;
    private final UserDetailsServiceCustom userDetailsService;
    private final ActiveSessionService activeSessionService;

    private static final String ACCESS_TOKEN_COOKIE_NAME = "access_token";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader(jwtConfig.getHeader());

        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(jwtConfig.getPrefix() + " ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = getJwtFromRequest(request);

        try {
            if (!jwtService.isValidToken(token)) {
                handleInvalidToken(response, getSpecificInvalidReason(token, request));
                return;
            }

            Claims claims = jwtService.extractClaims(token);
            String username = claims.getSubject();
            Integer userId = claims.get("userId", Integer.class);
            String tokenId = claims.getId();

            if (!activeSessionService.isCurrentAccessToken(username, tokenId)) {
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
            handleExpiredToken(response);

        } catch (JwtException ex) {
            SecurityContextHolder.clearContext();
            handleInvalidToken(response, "Invalid JWT token");

        } catch (Exception ex) {
            SecurityContextHolder.clearContext();
            handleAuthenticationException(response, ex);
        }
    }
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {

        String path = request.getServletPath();

        return path.equals("/jwt/login")
                || path.equals(jwtConfig.getUrl())
                || path.equals(jwtConfig.getRefreshUrl())
                || path.startsWith("/api/auth/")
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
            return bearerToken.substring((jwtConfig.getPrefix() + " ").length());
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
            if (jwtService.isBlacklisted(token)) {
                return "Token is revoked";
            }

            Claims claims = jwtService.extractClaims(token);
            String tokenDfp = claims.get("dfp", String.class);
            String reqDfp = jwtService.generateDeviceFingerprint(request);

            if (StringUtils.hasText(tokenDfp) &&
                    StringUtils.hasText(reqDfp) &&
                    !tokenDfp.equals(reqDfp)) {
                return "Device mismatch. Please login again.";
            }

            return "Invalid or expired token";

        } catch (ExpiredJwtException e) {
            return "Expired token";
        } catch (JwtException e) {
            return "Malformed or invalid token";
        } catch (Exception e) {
            return "Unauthorized";
        }
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
