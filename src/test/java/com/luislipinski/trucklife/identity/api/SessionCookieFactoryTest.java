package com.luislipinski.trucklife.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.luislipinski.trucklife.identity.config.IdentitySessionProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class SessionCookieFactoryTest {

    @Test
    void createsAndClearsAHostOnlySecureRefreshCookie() {
        SessionCookieFactory factory = new SessionCookieFactory(new IdentitySessionProperties(
                Duration.ofMinutes(10),
                Duration.ofDays(30),
                "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
                "https://api.example.com",
                "truck-life",
                "TLS_REFRESH_TOKEN",
                "/api/v1/auth"
        ));

        String issued = factory.refreshCookie("opaque-token").toString();
        String cleared = factory.clearedRefreshCookie().toString();

        assertThat(issued)
                .startsWith("TLS_REFRESH_TOKEN=opaque-token")
                .contains("Path=/api/v1/auth", "Max-Age=2592000", "Secure", "HttpOnly", "SameSite=None")
                .doesNotContain("Domain=");
        assertThat(cleared)
                .startsWith("TLS_REFRESH_TOKEN=")
                .contains("Max-Age=0", "Secure", "HttpOnly", "SameSite=None");
    }
}
