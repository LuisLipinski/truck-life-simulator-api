package com.luislipinski.trucklife.identity.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luislipinski.trucklife.shared.error.ApiSecurityProblemWriter;
import jakarta.servlet.http.Cookie;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RefreshCookieOriginFilterTest {

    private static final String ORIGIN = "https://app.example.com";
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final IdentityWebProperties webProperties = new IdentityWebProperties(
            List.of(ORIGIN),
            "TLS_CSRF_TOKEN"
    );
    private final CsrfTokenService csrfTokenService = new CsrfTokenService(
            new SecureRandom(),
            webProperties,
            new IdentitySessionProperties(
                    Duration.ofMinutes(10),
                    Duration.ofDays(30),
                    "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
                    "https://api.example.com",
                    "truck-life",
                    "TLS_REFRESH_TOKEN",
                    "/api/v1/auth"
            )
    );
    private final RefreshCookieOriginFilter filter = new RefreshCookieOriginFilter(
            webProperties,
            csrfTokenService,
            new ApiSecurityProblemWriter(objectMapper)
    );

    @Test
    void rejectsMissingOriginBeforeInspectingCsrf() throws Exception {
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(problemCode(response)).isEqualTo("ORIGIN_NOT_ALLOWED");
    }

    @Test
    void rejectsMissingCsrfForAnAllowedOrigin() throws Exception {
        MockHttpServletRequest request = request();
        request.addHeader(HttpHeaders.ORIGIN, ORIGIN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(problemCode(response)).isEqualTo("CSRF_TOKEN_INVALID");
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .isEqualTo(ORIGIN);
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS))
                .isEqualTo("true");
    }

    @Test
    void allowsTheRequestWhenOriginAndDoubleSubmitTokenMatch() throws Exception {
        MockHttpServletResponse csrfResponse = new MockHttpServletResponse();
        String token = csrfTokenService.issue(csrfResponse);
        MockHttpServletRequest request = request();
        request.addHeader(HttpHeaders.ORIGIN, ORIGIN);
        request.addHeader(CsrfTokenService.HEADER_NAME, token);
        request.setCookies(new Cookie("TLS_CSRF_TOKEN", token));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private MockHttpServletRequest request() {
        return new MockHttpServletRequest("POST", "/api/v1/auth/refresh");
    }

    private String problemCode(MockHttpServletResponse response) throws Exception {
        JsonNode problem = objectMapper.readTree(response.getContentAsByteArray());
        return problem.has("code")
                ? problem.path("code").asText()
                : problem.path("properties").path("code").asText();
    }
}
