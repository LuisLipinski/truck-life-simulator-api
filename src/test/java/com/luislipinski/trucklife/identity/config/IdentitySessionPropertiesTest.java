package com.luislipinski.trucklife.identity.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class IdentitySessionPropertiesTest {

    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(10);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(30);
    private static final String VALID_SECRET =
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Test
    void normalizesTextConfigurationAndRedactsTheJwtSecret() {
        IdentitySessionProperties properties = properties(
                ACCESS_TOKEN_TTL,
                REFRESH_TOKEN_TTL,
                "  " + VALID_SECRET + "  ",
                "  https://api.example.com  ",
                "  truck-life  ",
                "  TLS_REFRESH_TOKEN  ",
                "  /api/v1/auth  "
        );

        assertThat(properties.jwtSecretBase64()).isEqualTo(VALID_SECRET);
        assertThat(properties.issuer()).isEqualTo("https://api.example.com");
        assertThat(properties.audience()).isEqualTo("truck-life");
        assertThat(properties.refreshCookieName()).isEqualTo("TLS_REFRESH_TOKEN");
        assertThat(properties.refreshCookiePath()).isEqualTo("/api/v1/auth");
        assertThat(properties.toString())
                .contains("jwtSecretBase64=redacted")
                .doesNotContain(VALID_SECRET);
    }

    @Test
    void rejectsWeakAndMalformedJwtSecrets() {
        assertThatThrownBy(() -> properties(
                ACCESS_TOKEN_TTL,
                REFRESH_TOKEN_TTL,
                "dG9vLXNob3J0",
                "https://api.example.com",
                "truck-life",
                "TLS_REFRESH_TOKEN",
                "/api/v1/auth"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 32 bytes");

        assertThatThrownBy(() -> properties(
                ACCESS_TOKEN_TTL,
                REFRESH_TOKEN_TTL,
                "not valid base64 %%%",
                "https://api.example.com",
                "truck-life",
                "TLS_REFRESH_TOKEN",
                "/api/v1/auth"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid Base64");
    }

    @Test
    void requiresPositiveTokenDurationsAndLongerRefreshLifetime() {
        assertThatThrownBy(() -> properties(
                Duration.ZERO,
                REFRESH_TOKEN_TTL,
                VALID_SECRET,
                "https://api.example.com",
                "truck-life",
                "TLS_REFRESH_TOKEN",
                "/api/v1/auth"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("access-token-ttl must be positive");

        assertThatThrownBy(() -> properties(
                ACCESS_TOKEN_TTL,
                Duration.ofSeconds(-1),
                VALID_SECRET,
                "https://api.example.com",
                "truck-life",
                "TLS_REFRESH_TOKEN",
                "/api/v1/auth"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("refresh-token-ttl must be positive");

        assertThatThrownBy(() -> properties(
                ACCESS_TOKEN_TTL,
                ACCESS_TOKEN_TTL,
                VALID_SECRET,
                "https://api.example.com",
                "truck-life",
                "TLS_REFRESH_TOKEN",
                "/api/v1/auth"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must exceed");
    }

    @Test
    void requiresEveryTextSetting() {
        assertThatThrownBy(() -> properties(
                ACCESS_TOKEN_TTL,
                REFRESH_TOKEN_TTL,
                null,
                "https://api.example.com",
                "truck-life",
                "TLS_REFRESH_TOKEN",
                "/api/v1/auth"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jwt-secret-base64 must be configured");

        assertThatThrownBy(() -> properties(
                ACCESS_TOKEN_TTL,
                REFRESH_TOKEN_TTL,
                VALID_SECRET,
                "   ",
                "truck-life",
                "TLS_REFRESH_TOKEN",
                "/api/v1/auth"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("issuer must be configured");

        assertThatThrownBy(() -> properties(
                ACCESS_TOKEN_TTL,
                REFRESH_TOKEN_TTL,
                VALID_SECRET,
                "https://api.example.com",
                null,
                "TLS_REFRESH_TOKEN",
                "/api/v1/auth"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("audience must be configured");

        assertThatThrownBy(() -> properties(
                ACCESS_TOKEN_TTL,
                REFRESH_TOKEN_TTL,
                VALID_SECRET,
                "https://api.example.com",
                "truck-life",
                " ",
                "/api/v1/auth"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("refresh-cookie-name must be configured");

        assertThatThrownBy(() -> properties(
                ACCESS_TOKEN_TTL,
                REFRESH_TOKEN_TTL,
                VALID_SECRET,
                "https://api.example.com",
                "truck-life",
                "TLS_REFRESH_TOKEN",
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("refresh-cookie-path must be configured");
    }

    @Test
    void requiresAnAbsoluteRefreshCookiePath() {
        assertThatThrownBy(() -> properties(
                ACCESS_TOKEN_TTL,
                REFRESH_TOKEN_TTL,
                VALID_SECRET,
                "https://api.example.com",
                "truck-life",
                "TLS_REFRESH_TOKEN",
                "api/v1/auth"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("absolute path");
    }

    private IdentitySessionProperties properties(
            Duration accessTokenTtl,
            Duration refreshTokenTtl,
            String jwtSecretBase64,
            String issuer,
            String audience,
            String refreshCookieName,
            String refreshCookiePath
    ) {
        return new IdentitySessionProperties(
                accessTokenTtl,
                refreshTokenTtl,
                jwtSecretBase64,
                issuer,
                audience,
                refreshCookieName,
                refreshCookiePath
        );
    }
}
