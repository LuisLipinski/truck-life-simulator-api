package com.luislipinski.trucklife.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SafeHttpRequestLoggingFilterTest {

    private final SafeHttpRequestLoggingFilter filter = new SafeHttpRequestLoggingFilter();
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(SafeHttpRequestLoggingFilter.class);
        logger.setLevel(Level.INFO);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void logsAuthenticationMetadataWithoutSensitiveRequestData() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setQueryString("access_token=query-secret");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer header-secret");
        request.setCookies(new Cookie("TLS_REFRESH_TOKEN", "cookie-secret"));
        request.setContentType("application/json");
        request.setContent(
                "{\"email\":\"driver@example.com\",\"password\":\"password-secret\"}"
                        .getBytes(StandardCharsets.UTF_8)
        );
        request.setAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE, "corr-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                ((MockHttpServletResponse) servletResponse).setStatus(401)
        );

        assertThat(appender.list).hasSize(1);
        String message = appender.list.get(0).getFormattedMessage();

        assertThat(message)
                .contains("event=AUTH_LOGIN")
                .contains("method=POST")
                .contains("path=/api/v1/auth/login")
                .contains("status=401")
                .contains("outcome=REJECTED")
                .contains("correlationId=corr-123")
                .doesNotContain("query-secret")
                .doesNotContain("header-secret")
                .doesNotContain("cookie-secret")
                .doesNotContain("password-secret")
                .doesNotContain("driver@example.com");
    }

    @Test
    void skipsNonApiRequestsToAvoidHealthCheckNoise() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health/readiness");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
        });

        assertThat(appender.list).isEmpty();
    }
}
