package com.luislipinski.trucklife.identity.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class IdentityWebPropertiesTest {

    @Test
    void normalizesDeduplicatesAndAcceptsSecureOrLocalOrigins() {
        IdentityWebProperties properties = new IdentityWebProperties(
                List.of(
                        "  https://app.example.com  ",
                        "https://app.example.com",
                        "http://localhost:5173",
                        "http://127.0.0.1:5173"
                ),
                "  TLS_CSRF_TOKEN  "
        );

        assertThat(properties.allowedOrigins()).containsExactly(
                "https://app.example.com",
                "http://localhost:5173",
                "http://127.0.0.1:5173"
        );
        assertThat(properties.csrfCookieName()).isEqualTo("TLS_CSRF_TOKEN");
    }

    @Test
    void requiresAtLeastOneAllowedOrigin() {
        assertThatThrownBy(() -> new IdentityWebProperties(null, "TLS_CSRF_TOKEN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowed-origins");

        assertThatThrownBy(() -> new IdentityWebProperties(List.of(), "TLS_CSRF_TOKEN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowed-origins");
    }

    @Test
    void requiresTheCsrfCookieName() {
        assertThatThrownBy(() -> new IdentityWebProperties(
                List.of("https://app.example.com"),
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("csrf-cookie-name");

        assertThatThrownBy(() -> new IdentityWebProperties(
                List.of("https://app.example.com"),
                "   "
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("csrf-cookie-name");
    }

    @Test
    void rejectsBlankWildcardAndMalformedOrigins() {
        assertThatThrownBy(() -> new IdentityWebProperties(
                List.of("   "),
                "TLS_CSRF_TOKEN"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("explicit");

        assertThatThrownBy(() -> new IdentityWebProperties(
                List.of("https://*.example.com"),
                "TLS_CSRF_TOKEN"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("explicit");

        assertThatThrownBy(() -> new IdentityWebProperties(
                List.of("https://exa mple.com"),
                "TLS_CSRF_TOKEN"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid URI");
    }

    @Test
    void rejectsInsecureRemoteOriginsAndOriginsWithExtraUrlParts() {
        assertThatThrownBy(() -> new IdentityWebProperties(
                List.of("http://app.example.com"),
                "TLS_CSRF_TOKEN"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTPS");

        assertThatThrownBy(() -> new IdentityWebProperties(
                List.of("https://app.example.com/path"),
                "TLS_CSRF_TOKEN"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no path");

        assertThatThrownBy(() -> new IdentityWebProperties(
                List.of("https://app.example.com?source=test"),
                "TLS_CSRF_TOKEN"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no path");

        assertThatThrownBy(() -> new IdentityWebProperties(
                List.of("https://app.example.com#fragment"),
                "TLS_CSRF_TOKEN"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no path");
    }
}
