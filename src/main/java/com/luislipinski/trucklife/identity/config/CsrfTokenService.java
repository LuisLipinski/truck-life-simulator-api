package com.luislipinski.trucklife.identity.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class CsrfTokenService {

    public static final String HEADER_NAME = "X-CSRF-TOKEN";
    private static final int TOKEN_BYTES = 32;
    private static final Pattern TOKEN_FORMAT = Pattern.compile("[A-Za-z0-9_-]{43}");

    private final SecureRandom secureRandom;
    private final IdentityWebProperties webProperties;
    private final IdentitySessionProperties sessionProperties;

    public CsrfTokenService(
            SecureRandom secureRandom,
            IdentityWebProperties webProperties,
            IdentitySessionProperties sessionProperties
    ) {
        this.secureRandom = secureRandom;
        this.webProperties = webProperties;
        this.sessionProperties = sessionProperties;
    }

    public String issue(HttpServletResponse response) {
        byte[] tokenBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        response.addHeader(HttpHeaders.SET_COOKIE, csrfCookie(token).build().toString());
        return token;
    }

    public boolean isValid(HttpServletRequest request) {
        String headerToken = request.getHeader(HEADER_NAME);
        String cookieToken = readCookie(request, webProperties.csrfCookieName());
        if (headerToken == null || cookieToken == null
                || !TOKEN_FORMAT.matcher(headerToken).matches()
                || !TOKEN_FORMAT.matcher(cookieToken).matches()) {
            return false;
        }
        return MessageDigest.isEqual(
                headerToken.getBytes(StandardCharsets.US_ASCII),
                cookieToken.getBytes(StandardCharsets.US_ASCII)
        );
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, csrfCookie("")
                .maxAge(0)
                .build()
                .toString());
    }

    private String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private ResponseCookie.ResponseCookieBuilder csrfCookie(String value) {
        return ResponseCookie.from(webProperties.csrfCookieName(), value)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path(sessionProperties.refreshCookiePath());
    }
}
