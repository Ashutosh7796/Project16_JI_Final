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
 * accidentally match SQL keywords, corrupting JWTs and causing Malformed JWT errors.
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
     * Request wrapper that sanitizes parameters to prevent SQL injection attacks
     */
    private static class SqlInjectionRequestWrapper extends HttpServletRequestWrapper {

        private static final Pattern[] SQL_INJECTION_PATTERNS = {
            Pattern.compile("(?i)\\b(select|insert|update|delete|from|where|drop|alter|truncate|exec|union|create|table|into|procedure|schema)\\b.*?\\b(.*?)\\b"),
            Pattern.compile("(?i)/\\*.*?\\*/|--.*?$"),
            // SQL operators
            Pattern.compile("(?i)\\b(and|or|not|like|between|in|is|null)\\b.*?\\b(.*?)\\b"),
            // SQL functions
            Pattern.compile("(?i)\\b(count|sum|avg|min|max)\\b.*?\\(.*?\\)"),
            // Multiple statements
            Pattern.compile(";.*?$"),
            // Equals with quotes
            Pattern.compile("'\\s*=\\s*'"),
            // Always true conditions
            Pattern.compile("'\\s*or\\s*'\\s*'\\s*=\\s*'"),
            // Batched statements
            Pattern.compile(";\\s*\\w+.*?"),
            // UNION-based attacks
            Pattern.compile("(?i)union\\s+all\\s+select"),
            // Time-based blind attacks
            Pattern.compile("(?i)sleep\\s*\\(\\s*\\d+\\s*\\)|benchmark\\s*\\("),
            // Error-based attacks
            Pattern.compile("(?i)extractvalue\\s*\\(|updatexml\\s*\\("),
            // Stacked queries
            Pattern.compile(";\\s*\\w+.*?"),
            // Hex encoding
            Pattern.compile("(?i)0x[0-9a-f]+")
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
         * Sanitizes the given value to prevent SQL injection attacks
         */
        private String sanitize(String value) {
            if (value == null) {
                return null;
            }

            String sanitizedValue = value;
            for (Pattern pattern : SQL_INJECTION_PATTERNS) {
                sanitizedValue = pattern.matcher(sanitizedValue).replaceAll("");
            }

            sanitizedValue = sanitizedValue
                .replaceAll("'", "")
                .replaceAll("\"", "")
                .replaceAll(";", "")
                .replaceAll("--", "")
                .replaceAll("/\\*", "")
                .replaceAll("\\*/", "")
                .replaceAll("#", "");
            
            return sanitizedValue;
        }
    }
} 