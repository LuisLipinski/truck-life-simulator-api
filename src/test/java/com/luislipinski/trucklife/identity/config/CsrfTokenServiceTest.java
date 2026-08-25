package com.luislipinski.trucklife.identity.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CsrfTokenServiceTest {

    @Test
    void issuesAProtectedCookieAndRequiresTheSameTokenInTheHeader() {
        CsrfTokenService service = new CsrfTokenService(
                new SecureRandom(),
                new IdentityWebProperties(
                        List.of("https://app.example.com"),
                        "TLS_CSRF_TOKEN"
                ),
                sessionProperties()
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        String token = service.issue(response);

        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        assertThat(response.getHeader("Set-Cookie"))
                .startsWith("TLS_CSRF_TOKEN=" + token)
                .contains("Path=/api/v1/auth", "Secure", "HttpOnly", "SameSite=None");

        MockHttpServletRequest valid = new MockHttpServletRequest();
        valid.setCookies(new Cookie("TLS_CSRF_TOKEN", token));
        valid.addHeader(CsrfTokenService.HEADER_NAME, token);
        assertThat(service.isValid(valid)).isTrue();

        MockHttpServletRequest invalid = new MockHttpServletRequest();
        invalid.setCookies(new Cookie("TLS_CSRF_TOKEN", token));
        invalid.addHeader(CsrfTokenService.HEADER_NAME, "A".repeat(43));
        assertThat(service.isValid(invalid)).isFalse();
    }

    private IdentitySessionProperties sessionProperties() {
        return new IdentitySessionProperties(
                Duration.ofMinutes(10),
                Duration.ofDays(30),
                "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
                "https://api.example.com",
                "truck-life",
                "TLS_REFRESH_TOKEN",
                "/api/v1/auth"
        );
    }
}
