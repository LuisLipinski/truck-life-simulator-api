package com.luislipinski.trucklife.identity.application;

import java.time.Instant;

public final class IssuedSession {

    private final String accessToken;
    private final Instant accessTokenExpiresAt;
    private final String rawRefreshToken;
    private final Instant refreshTokenExpiresAt;

    public IssuedSession(
            String accessToken,
            Instant accessTokenExpiresAt,
            String rawRefreshToken,
            Instant refreshTokenExpiresAt
    ) {
        this.accessToken = accessToken;
        this.accessTokenExpiresAt = accessTokenExpiresAt;
        this.rawRefreshToken = rawRefreshToken;
        this.refreshTokenExpiresAt = refreshTokenExpiresAt;
    }

    public String accessToken() {
        return accessToken;
    }

    public Instant accessTokenExpiresAt() {
        return accessTokenExpiresAt;
    }

    public String rawRefreshToken() {
        return rawRefreshToken;
    }

    public Instant refreshTokenExpiresAt() {
        return refreshTokenExpiresAt;
    }

    @Override
    public String toString() {
        return "IssuedSession[accessToken=redacted, accessTokenExpiresAt="
                + accessTokenExpiresAt
                + ", rawRefreshToken=redacted, refreshTokenExpiresAt="
                + refreshTokenExpiresAt
                + ']';
    }
}
