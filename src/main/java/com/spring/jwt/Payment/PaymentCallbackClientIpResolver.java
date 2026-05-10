package com.spring.jwt.Payment;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Resolves the client IP for a callback request. {@code X-Forwarded-For} is trusted only when the
 * direct caller (TCP peer) is in the configured trusted-proxy list — defends against IP spoofing
 * by attackers who simply set the header themselves.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCallbackClientIpResolver {

    private final PaymentCallbackSecurityProperties properties;

    public String resolve(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff == null || xff.isBlank()) {
            return remoteAddr;
        }
        // Walk RIGHT-to-LEFT: the rightmost entry is set by the trusted proxy itself, each
        // earlier hop must also be a trusted proxy for us to accept the next-hop value.
        List<String> chain = new ArrayList<>(Arrays.asList(xff.split(",")));
        for (int i = chain.size() - 1; i >= 0; i--) {
            String hop = chain.get(i).trim();
            if (i == 0) {
                return hop;
            }
            if (!isTrustedProxy(hop)) {
                return hop;
            }
        }
        return remoteAddr;
    }

    /** True when {@code candidate} matches any configured trusted-proxy CIDR or exact IP. */
    public boolean isTrustedProxy(String candidate) {
        return CidrMatch.anyMatches(properties.getTrustedProxies(), candidate);
    }

    public boolean isAllowedCallbackSource(String candidate) {
        List<String> allow = properties.getAllowedCidrs();
        if (allow == null || allow.isEmpty()) {
            return true; // unset == permissive (preserves behavior; lock down per environment)
        }
        return CidrMatch.anyMatches(allow, candidate);
    }

    /** Tiny CIDR matcher with no extra dependencies; supports IPv4 and IPv6. */
    static final class CidrMatch {
        private CidrMatch() {}

        static boolean anyMatches(List<String> patterns, String candidate) {
            if (patterns == null || patterns.isEmpty() || candidate == null || candidate.isBlank()) {
                return false;
            }
            byte[] candidateBytes;
            try {
                candidateBytes = InetAddress.getByName(candidate).getAddress();
            } catch (UnknownHostException e) {
                return false;
            }
            for (String pattern : patterns) {
                if (pattern == null) continue;
                String trimmed = pattern.trim();
                if (trimmed.isEmpty()) continue;
                if (matches(trimmed, candidate, candidateBytes)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean matches(String pattern, String candidate, byte[] candidateBytes) {
            int slash = pattern.indexOf('/');
            if (slash < 0) {
                return pattern.equalsIgnoreCase(candidate);
            }
            String network = pattern.substring(0, slash);
            int prefix;
            try {
                prefix = Integer.parseInt(pattern.substring(slash + 1));
            } catch (NumberFormatException e) {
                return false;
            }
            byte[] networkBytes;
            try {
                networkBytes = InetAddress.getByName(network).getAddress();
            } catch (UnknownHostException e) {
                return false;
            }
            if (networkBytes.length != candidateBytes.length) {
                return false;
            }
            int fullBytes = prefix / 8;
            int remainingBits = prefix % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (networkBytes[i] != candidateBytes[i]) {
                    return false;
                }
            }
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xFF << (8 - remainingBits);
            return (networkBytes[fullBytes] & mask) == (candidateBytes[fullBytes] & mask);
        }
    }
}
