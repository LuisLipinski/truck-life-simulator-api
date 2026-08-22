package com.luislipinski.trucklife.identity.config;

import java.time.Duration;
import java.util.Base64;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("identity.session")
public record IdentitySessionProperties(
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        String jwtSecretBase64,
        String issuer,
        String audience,
        String refreshCookieName,
        String refreshCookiePath
) {

    private static final int MINIMUM_SECRET_BYTES = 32;

    public IdentitySessionProperties {
        jwtSecretBase64 = jwtSecretBase64 == null ? null : jwtSecretBase64.strip();
        issuer = issuer == null ? null : issuer.strip();
        audience = audience == null ? null : audience.strip();
        refreshCookieName = refreshCookieName == null ? null : refreshCookieName.strip();
        refreshCookiePath = refreshCookiePath == null ? null : refreshCookiePath.strip();
        requirePositive(accessTokenTtl, "identity.session.access-token-ttl");
        requirePositive(refreshTokenTtl, "identity.session.refresh-token-ttl");
        if (refreshTokenTtl.compareTo(accessTokenTtl) <= 0) {
            throw new IllegalArgumentException(
                    "identity.session.refresh-token-ttl must exceed access-token-ttl"
            );
        }
        requireText(jwtSecretBase64, "identity.session.jwt-secret-base64");
        requireText(issuer, "identity.session.issuer");
        requireText(audience, "identity.session.audience");
        requireText(refreshCookieName, "identity.session.refresh-cookie-name");
        requireText(refreshCookiePath, "identity.session.refresh-cookie-path");

        byte[] secret;
        try {
            secret = Base64.getDecoder().decode(jwtSecretBase64);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "identity.session.jwt-secret-base64 must be valid Base64",
                    exception
            );
        }
        if (secret.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "identity.session.jwt-secret-base64 must decode to at least 32 bytes"
            );
        }
        if (!refreshCookiePath.startsWith("/")) {
            throw new IllegalArgumentException(
                    "identity.session.refresh-cookie-path must be an absolute path"
            );
        }
    }

    @Override
    public String toString() {
        return "IdentitySessionProperties[accessTokenTtl="
                + accessTokenTtl
                + ", refreshTokenTtl="
                + refreshTokenTtl
                + ", jwtSecretBase64=redacted, issuer="
                + issuer
                + ", audience="
                + audience
                + ", refreshCookieName="
                + refreshCookieName
                + ", refreshCookiePath="
                + refreshCookiePath
                + ']';
    }

    private static void requirePositive(Duration duration, String propertyName) {
        Objects.requireNonNull(duration, propertyName + " must be configured");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(propertyName + " must be positive");
        }
    }

    private static void requireText(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(propertyName + " must be configured");
        }
    }
}
