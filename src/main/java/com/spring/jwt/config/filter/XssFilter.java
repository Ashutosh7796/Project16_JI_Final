package com.spring.jwt.config.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Filter to protect against XSS attacks by sanitizing request parameters and form data.
 * <p>
 * Headers in {@code EXCLUDED_HEADERS} (Authorization, Cookie, etc.) are never sanitized
 * because they carry opaque tokens whose content must not be mutated.
 * <p>
 * Parameter sanitization strips dangerous script/event patterns but preserves legitimate
 * characters like {@code /}, {@code '}, and properly orders HTML entity encoding to
 * prevent double-encoding.
 */
@Component
public class XssFilter implements Filter, Ordered {

    private static final Set<String> EXCLUDED_HEADERS = new HashSet<>(Arrays.asList(
            "accept", "content-type", "authorization", "origin", "referer", "user-agent",
            "host", "connection", "content-length", "cookie", "accept-encoding", "accept-language"
    ));

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;

        if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        
        String path = httpRequest.getRequestURI();

        if (path.startsWith("/user/registerUser")) {
            chain.doFilter(request, response);
            return;
        }

        if (path.contains("swagger-ui") || path.contains("api-docs")) {
            chain.doFilter(request, response);
            return;
        }

        XssRequestWrapper wrappedRequest = new XssRequestWrapper((HttpServletRequest) request);
        chain.doFilter(wrappedRequest, response);
    }

    @Override
    public void init(FilterConfig filterConfig)
    {

    }

    @Override
    public void destroy()
    {
    }

    @Override
    public int getOrder()
    {
        return Ordered.HIGHEST_PRECEDENCE + 40;
    }

    /**
     * Request wrapper that sanitizes parameters to prevent XSS attacks
     */
    private static class XssRequestWrapper extends HttpServletRequestWrapper {

        private static final Pattern[] XSS_PATTERNS = {
                // Script tags
                Pattern.compile("<script[^>]*>(.*?)</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
                // src attributes
                Pattern.compile("src[\\r\\n]*=[\\r\\n]*\\'(.*?)\\'", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
                Pattern.compile("src[\\r\\n]*=[\\r\\n]*\\\"(.*?)\\\"", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
                // Event handlers (onclick, onerror, onload, etc.)
                Pattern.compile("on\\w+\\s*=\\s*\".*?\"", Pattern.CASE_INSENSITIVE),
                Pattern.compile("on\\w+\\s*=\\s*'.*?'", Pattern.CASE_INSENSITIVE),
                // javascript: protocol
                Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE),
                // CSS expressions
                Pattern.compile("expression\\(.*?\\)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
                Pattern.compile("behavior\\s*:\\s*url\\(.*?\\)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
                // Dangerous JS functions
                Pattern.compile("eval\\((.*?)\\)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
                Pattern.compile("document\\.write\\((.*?)\\)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
                Pattern.compile("document\\.cookie", Pattern.CASE_INSENSITIVE),
                // Dangerous tags (not generic tag matching — only specific dangerous ones)
                Pattern.compile("<iframe[^>]*>(.*?)</iframe>", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
                Pattern.compile("<object[^>]*>(.*?)</object>", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
                Pattern.compile("<embed[^>]*>", Pattern.CASE_INSENSITIVE),
                Pattern.compile("<applet[^>]*>(.*?)</applet>", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
                Pattern.compile("<form[^>]*>(.*?)</form>", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
        };

        private Map<String, String[]> sanitizedParameterMap;

        public XssRequestWrapper(HttpServletRequest request) {
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
            if (header != null && EXCLUDED_HEADERS.contains(name.toLowerCase())) {
                return header;
            }
            return header != null ? sanitize(header) : null;
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            Enumeration<String> headers = super.getHeaders(name);
            if (headers == null) {
                return null;
            }

            if (EXCLUDED_HEADERS.contains(name.toLowerCase())) {
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
         * Sanitizes the given value to prevent XSS attacks.
         * <p>
         * Only strips known-dangerous patterns (script tags, event handlers, etc.).
         * Does NOT blindly entity-encode all special characters — that would corrupt
         * legitimate data like URLs, dates with slashes, names with apostrophes, etc.
         */
        private String sanitize(String value) {
            if (value == null) {
                return null;
            }

            String sanitizedValue = value;
            for (Pattern pattern : XSS_PATTERNS) {
                sanitizedValue = pattern.matcher(sanitizedValue).replaceAll("");
            }

            // Only encode actual HTML angle brackets to prevent tag injection.
            // IMPORTANT: encode & FIRST to avoid double-encoding (&lt; → &amp;lt;)
            sanitizedValue = sanitizedValue
                    .replaceAll("&", "&amp;")
                    .replaceAll("<", "&lt;")
                    .replaceAll(">", "&gt;");

            // Do NOT encode: / (URLs, paths, dates), ' (names like O'Brien), " (common in text)
            // Those are handled by output encoding at the view layer, not input filtering.

            return sanitizedValue;
        }
    }
}