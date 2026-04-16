package com.spring.jwt.config.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Filter to protect against SQL injection attacks by sanitizing request parameters.
 * <p>
 * Headers that carry opaque bearer tokens or session cookies (Authorization, Cookie)
 * are <b>excluded</b> from sanitization because their base64url content can
 * accidentally match SQL keywords, corrupting JWTs.
 * <p>
 * Parameter sanitization only targets clear SQL injection patterns (multi-statement,
 * tautologies, UNION attacks). It does NOT strip legitimate characters like apostrophes,
 * quotes, semicolons, or hashes — those break names (O'Brien), hashtags, and text input.
 * SQL injection is properly prevented by parameterized queries (JPA/Hibernate), not
 * by mangling user input.
 */
@Component
public class SqlInjectionFilter implements Filter, Ordered {

    /** Headers whose values must never be mutated (lower-case for comparison). */
    private static final Set<String> EXCLUDED_HEADERS = Set.of(
            "authorization", "cookie", "set-cookie"
    );

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;

        if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        
        SqlInjectionRequestWrapper wrappedRequest = new SqlInjectionRequestWrapper(httpRequest);
        chain.doFilter(wrappedRequest, response);
    }

    @Override
    public void init(FilterConfig filterConfig) {

    }

    @Override
    public void destroy() {
    }
    
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 45;
    }
    
    /**
     * Request wrapper that sanitizes parameters to prevent SQL injection attacks.
     * <p>
     * IMPORTANT: The primary defense against SQL injection is parameterized queries
     * (JPA/Hibernate). This filter is a defense-in-depth measure that only targets
     * clear attack patterns, not legitimate text containing SQL keywords.
     */
    private static class SqlInjectionRequestWrapper extends HttpServletRequestWrapper {

        /**
         * Only match clear SQL injection attack patterns — NOT isolated SQL keywords
         * in normal text. A search field containing "select the best option from menu"
         * should NOT be stripped.
         */
        private static final Pattern[] SQL_INJECTION_PATTERNS = {
            // UNION-based injection (most common attack vector)
            Pattern.compile("(?i)union\\s+(all\\s+)?select\\b"),
            // Stacked queries: "; DROP TABLE" etc. (but not standalone semicolons)
            Pattern.compile(";\\s*(drop|alter|truncate|delete|insert|update|create|exec)\\b", Pattern.CASE_INSENSITIVE),
            // Always-true tautologies used in WHERE clause injection
            Pattern.compile("'\\s*or\\s+'\\s*'\\s*=\\s*'", Pattern.CASE_INSENSITIVE),
            Pattern.compile("'\\s*or\\s+1\\s*=\\s*1", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\"\\s*or\\s+\"\\s*\"\\s*=\\s*\"", Pattern.CASE_INSENSITIVE),
            // SQL comment injection (used to truncate queries)
            Pattern.compile("(?i)/\\*.*?\\*/"),
            Pattern.compile("--\\s+.*$", Pattern.MULTILINE),
            // Time-based blind injection
            Pattern.compile("(?i)\\bsleep\\s*\\(\\s*\\d+\\s*\\)"),
            Pattern.compile("(?i)\\bbenchmark\\s*\\("),
            // Error-based injection
            Pattern.compile("(?i)\\bextractvalue\\s*\\("),
            Pattern.compile("(?i)\\bupdatexml\\s*\\("),
        };

        private Map<String, String[]> sanitizedParameterMap;

        public SqlInjectionRequestWrapper(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getParameter(String name) {
            String parameter = super.getParameter(name);
            return parameter != null ? sanitize(parameter) : null;
        }

        @Override
        public String[] getParameterValues(String name) {
            String[] values = super.getParameterValues(name);
            if (values == null) {
                return null;
            }
            
            String[] sanitizedValues = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                sanitizedValues[i] = sanitize(values[i]);
            }
            
            return sanitizedValues;
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            if (sanitizedParameterMap == null) {
                Map<String, String[]> rawParameterMap = super.getParameterMap();
                sanitizedParameterMap = new HashMap<>(rawParameterMap.size());
                
                for (Map.Entry<String, String[]> entry : rawParameterMap.entrySet()) {
                    String[] rawValues = entry.getValue();
                    String[] sanitizedValues = new String[rawValues.length];
                    
                    for (int i = 0; i < rawValues.length; i++) {
                        sanitizedValues[i] = sanitize(rawValues[i]);
                    }
                    
                    sanitizedParameterMap.put(entry.getKey(), sanitizedValues);
                }
            }
            
            return Collections.unmodifiableMap(sanitizedParameterMap);
        }

        @Override
        public String getHeader(String name) {
            String header = super.getHeader(name);
            if (header == null) {
                return null;
            }
            // Never sanitize headers that carry opaque tokens (JWT, session cookies)
            if (EXCLUDED_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                return header;
            }
            return sanitize(header);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            Enumeration<String> headers = super.getHeaders(name);
            if (headers == null) {
                return null;
            }

            // Never sanitize headers that carry opaque tokens
            if (EXCLUDED_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                return headers;
            }

            return new Enumeration<String>() {
                @Override
                public boolean hasMoreElements() {
                    return headers.hasMoreElements();
                }

                @Override
                public String nextElement() {
                    return sanitize(headers.nextElement());
                }
            };
        }

        /**
         * Strips only clear SQL injection attack patterns.
         * Does NOT strip: apostrophes, quotes, semicolons, hashes, hex values.
         * Those are legitimate in user text and handled by parameterized queries.
         */
        private String sanitize(String value) {
            if (value == null) {
                return null;
            }

            String sanitizedValue = value;
            for (Pattern pattern : SQL_INJECTION_PATTERNS) {
                sanitizedValue = pattern.matcher(sanitizedValue).replaceAll("");
            }
            
            return sanitizedValue;
        }
    }
}