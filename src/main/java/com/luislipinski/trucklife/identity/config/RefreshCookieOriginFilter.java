package com.luislipinski.trucklife.identity.config;

import com.luislipinski.trucklife.shared.error.ApiSecurityProblemWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

public class RefreshCookieOriginFilter extends OncePerRequestFilter {

    private static final Set<String> COOKIE_SESSION_PATHS = Set.of(
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout"
    );

    private final Set<String> allowedOrigins;
    private final CsrfTokenService csrfTokenService;
    private final ApiSecurityProblemWriter problemWriter;

    public RefreshCookieOriginFilter(
            IdentityWebProperties webProperties,
            CsrfTokenService csrfTokenService,
            ApiSecurityProblemWriter problemWriter
    ) {
        this.allowedOrigins = Set.copyOf(webProperties.allowedOrigins());
        this.csrfTokenService = csrfTokenService;
        this.problemWriter = problemWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!isCookieSessionRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin == null || !allowedOrigins.contains(origin)) {
            problemWriter.write(
                    request,
                    response,
                    HttpStatus.FORBIDDEN,
                    "ORIGIN_NOT_ALLOWED",
                    "Origin not allowed",
                    "Cookie-backed session requests require an allowed Origin header"
            );
            return;
        }
        if (!csrfTokenService.isValid(request)) {
            allowFrontendToReadError(response, origin);
            problemWriter.write(
                    request,
                    response,
                    HttpStatus.FORBIDDEN,
                    "CSRF_TOKEN_INVALID",
                    "CSRF token invalid",
                    "A valid CSRF token is required for this request"
            );
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isCookieSessionRequest(HttpServletRequest request) {
        return "POST".equals(request.getMethod())
                && COOKIE_SESSION_PATHS.contains(request.getRequestURI());
    }

    private void allowFrontendToReadError(HttpServletResponse response, String origin) {
        response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
        response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        response.addHeader(HttpHeaders.VARY, HttpHeaders.ORIGIN);
    }
}
