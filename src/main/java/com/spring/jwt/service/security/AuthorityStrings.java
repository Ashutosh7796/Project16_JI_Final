package com.spring.jwt.service.security;

import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * Normalizes role / authority strings for Spring Security and JWT claims.
 * Only permits portable ASCII token characters so JWT JSON stays valid UTF-8
 * even if the database driver or ORM delivers unexpected characters.
 */
public final class AuthorityStrings {

    private static final String ROLE_PREFIX = "ROLE_";
    /** Spring authorities are short; cap size to avoid abuse and log noise. */
    public static final int MAX_AUTHORITY_LENGTH = 128;

    private AuthorityStrings() {
    }

    /**
     * Keeps {@code A–Z}, {@code a–z}, {@code 0–9}, and underscore; drops everything else.
     */
    public static String normalizeAuthorityToken(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        StringBuilder sb = new StringBuilder(Math.min(raw.length(), MAX_AUTHORITY_LENGTH));
        for (int i = 0; i < raw.length(); ) {
            int cp = raw.codePointAt(i);
            if (isSafeAuthorityCodePoint(cp)) {
                int add = Character.charCount(cp);
                if (sb.length() + add > MAX_AUTHORITY_LENGTH) {
                    break;
                }
                sb.appendCodePoint(cp);
            }
            i += Character.charCount(cp);
        }
        return sb.toString();
    }

    private static boolean isSafeAuthorityCodePoint(int cp) {
        return (cp >= 'A' && cp <= 'Z')
                || (cp >= 'a' && cp <= 'z')
                || (cp >= '0' && cp <= '9')
                || cp == '_';
    }

    /**
     * {@link org.springframework.security.core.authority.SimpleGrantedAuthority} value
     * from a role name as stored in the database (with or without {@code ROLE_} prefix).
     */
    public static String springAuthorityFromDatabaseRole(String databaseRoleName) {
        String token = normalizeAuthorityToken(databaseRoleName).toUpperCase(Locale.ROOT);
        if (!StringUtils.hasText(token)) {
            return ROLE_PREFIX + "USER";
        }
        if (token.startsWith(ROLE_PREFIX)) {
            return truncate(token);
        }
        return truncate(ROLE_PREFIX + token);
    }

    /**
     * Normalizes a Spring {@code GrantedAuthority#getAuthority()} string for a JWT {@code authorities} claim.
     */
    public static String forJwtClaim(String grantedAuthority) {
        return springAuthorityFromDatabaseRole(grantedAuthority);
    }

    private static String truncate(String s) {
        return s.length() <= MAX_AUTHORITY_LENGTH ? s : s.substring(0, MAX_AUTHORITY_LENGTH);
    }
}
