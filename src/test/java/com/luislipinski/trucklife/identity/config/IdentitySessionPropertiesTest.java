package com.luislipinski.trucklife.identity.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class IdentitySessionPropertiesTest {

    @Test
    void rejectsWeakJwtSecretsAndWildcardOrigins() {
        assertThatThrownBy(() -> new IdentitySessionProperties(
                Duration.ofMinutes(10),
                Duration.ofDays(30),
                "dG9vLXNob3J0",
                "https://api.example.com",
                "truck-life",
                "TLS_REFRESH_TOKEN",
                "/api/v1/auth"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 32 bytes");

        assertThatThrownBy(() -> new IdentityWebProperties(
                List.of("*"),
                "TLS_CSRF_TOKEN"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("explicit");
    }

    @Test
    void onlyAllowsHttpForLocalDevelopmentOrigins() {
        new IdentityWebProperties(
                List.of("http://localhost:5173", "https://app.example.com"),
                "TLS_CSRF_TOKEN"
        );

        assertThatThrownBy(() -> new IdentityWebProperties(
                List.of("http://app.example.com"),
                "TLS_CSRF_TOKEN"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTPS");
    }

    @Test
    void requiresRefreshTokensToOutliveAccessTokens() {
        assertThatThrownBy(() -> new IdentitySessionProperties(
                Duration.ofMinutes(10),
                Duration.ofMinutes(10),
                "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
                "https://api.example.com",
                "truck-life",
                "TLS_REFRESH_TOKEN",
                "/api/v1/auth"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must exceed");
    }
}
