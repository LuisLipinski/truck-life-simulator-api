package com.luislipinski.trucklife.shared.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class SafeHttpRequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(SafeHttpRequestLoggingFilter.class);
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("[\\r\\n\\t]");
    private static final int MAX_PATH_LENGTH = 512;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return requestUri == null || !requestUri.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        boolean failedOutsideMvc = false;

        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException exception) {
            failedOutsideMvc = true;
            throw exception;
        } finally {
            logCompletedRequest(request, response, startedAt, failedOutsideMvc);
        }
    }

    private void logCompletedRequest(
            HttpServletRequest request,
            HttpServletResponse response,
            long startedAt,
            boolean failedOutsideMvc
    ) {
        String method = sanitize(request.getMethod(), 16);
        String path = sanitize(request.getRequestURI(), MAX_PATH_LENGTH);
        int status = response.getStatus();
        if (failedOutsideMvc && status < 400) {
            status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        }

        long durationMs = Math.max(0, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
        String event = eventName(method, path);
        String outcome = outcome(status);
        Object correlationId = request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
        String correlation = correlationId == null ? "missing" : sanitize(correlationId.toString(), 128);

        String message = "http_request event={} method={} path={} status={} outcome={} durationMs={} correlationId={}";
        if (status >= 500) {
            LOGGER.error(message, event, method, path, status, outcome, durationMs, correlation);
        } else if (status >= 400) {
            LOGGER.warn(message, event, method, path, status, outcome, durationMs, correlation);
        } else {
            LOGGER.info(message, event, method, path, status, outcome, durationMs, correlation);
        }
    }

    private String eventName(String method, String path) {
        return switch (method + " " + path) {
            case "POST /api/v1/auth/register" -> "AUTH_REGISTER";
            case "POST /api/v1/auth/verify-email" -> "AUTH_VERIFY_EMAIL";
            case "POST /api/v1/auth/resend-verification" -> "AUTH_RESEND_VERIFICATION";
            case "POST /api/v1/auth/login" -> "AUTH_LOGIN";
            case "POST /api/v1/auth/refresh" -> "AUTH_REFRESH";
            case "POST /api/v1/auth/logout" -> "AUTH_LOGOUT";
            case "POST /api/v1/auth/forgot-password" -> "AUTH_FORGOT_PASSWORD";
            case "POST /api/v1/auth/reset-password" -> "AUTH_RESET_PASSWORD";
            case "GET /api/v1/auth/csrf" -> "AUTH_CSRF";
            case "GET /api/v1/me" -> "ACCOUNT_ME";
            case "GET /api/v1/platform" -> "PLATFORM_STATUS";
            default -> "API_REQUEST";
        };
    }

    private String outcome(int status) {
        if (status >= 500) return "ERROR";
        if (status >= 400) return "REJECTED";
        return "SUCCESS";
    }

    private String sanitize(String value, int maxLength) {
        if (value == null || value.isBlank()) return "unknown";
        String sanitized = CONTROL_CHARACTERS.matcher(value).replaceAll("_");
        return sanitized.length() <= maxLength ? sanitized : sanitized.substring(0, maxLength);
    }
}
