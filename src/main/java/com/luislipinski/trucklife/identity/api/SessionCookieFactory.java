package com.luislipinski.trucklife.identity.api;

import com.luislipinski.trucklife.identity.config.IdentitySessionProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class SessionCookieFactory {

    private final IdentitySessionProperties properties;

    public SessionCookieFactory(IdentitySessionProperties properties) {
        this.properties = properties;
    }

    public String cookieName() {
        return properties.refreshCookieName();
    }

    public ResponseCookie refreshCookie(String rawRefreshToken) {
        return baseCookie(rawRefreshToken)
                .maxAge(properties.refreshTokenTtl())
                .build();
    }

    public ResponseCookie clearedRefreshCookie() {
        return baseCookie("")
                .maxAge(0)
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(properties.refreshCookieName(), value)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path(properties.refreshCookiePath());
    }
}
