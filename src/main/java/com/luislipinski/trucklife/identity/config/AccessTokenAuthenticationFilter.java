package com.luislipinski.trucklife.identity.config;

import com.luislipinski.trucklife.identity.application.AuthenticatedAccount;
import com.luislipinski.trucklife.identity.application.JwtAccessTokenIssuer;
import com.luislipinski.trucklife.identity.domain.UserStatus;
import com.luislipinski.trucklife.identity.persistence.UserEntity;
import com.luislipinski.trucklife.identity.persistence.UserRepository;
import com.luislipinski.trucklife.shared.error.ApiSecurityProblemWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

public class AccessTokenAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTHENTICATED_ACCOUNT_ATTRIBUTE =
            AccessTokenAuthenticationFilter.class.getName() + ".authenticatedAccount";
    private static final String ME_PATH = "/api/v1/me";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtAccessTokenIssuer accessTokenIssuer;
    private final UserRepository userRepository;
    private final ApiSecurityProblemWriter problemWriter;

    public AccessTokenAuthenticationFilter(
            JwtAccessTokenIssuer accessTokenIssuer,
            UserRepository userRepository,
            ApiSecurityProblemWriter problemWriter
    ) {
        this.accessTokenIssuer = accessTokenIssuer;
        this.userRepository = userRepository;
        this.problemWriter = problemWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !ME_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || authorization.isBlank()) {
            writeUnauthorized(request, response, "AUTHENTICATION_REQUIRED", "A Bearer access token is required");
            return;
        }
        if (!authorization.startsWith(BEARER_PREFIX)) {
            writeUnauthorized(request, response, "ACCESS_TOKEN_INVALID", "The access token is invalid or expired");
            return;
        }

        String rawToken = authorization.substring(BEARER_PREFIX.length()).strip();
        JwtAccessTokenIssuer.DecodedAccessToken token;
        try {
            token = accessTokenIssuer.decodeAndValidate(rawToken);
        } catch (JwtAccessTokenIssuer.InvalidAccessTokenException exception) {
            writeUnauthorized(request, response, "ACCESS_TOKEN_INVALID", "The access token is invalid or expired");
            return;
        }

        UserEntity user = userRepository.findById(token.userId()).orElse(null);
        if (user == null
                || user.getRole() != token.role()
                || user.isEmailVerified() != token.emailVerified()) {
            writeUnauthorized(request, response, "ACCESS_TOKEN_INVALID", "The access token is no longer valid");
            return;
        }
        if (user.getStatus() != UserStatus.ACTIVE || !user.isEmailVerified()) {
            problemWriter.write(
                    request,
                    response,
                    HttpStatus.FORBIDDEN,
                    "ACCOUNT_FORBIDDEN",
                    "Account access forbidden",
                    "The account cannot access protected resources"
            );
            return;
        }

        request.setAttribute(
                AUTHENTICATED_ACCOUNT_ATTRIBUTE,
                new AuthenticatedAccount(
                        user.getId(),
                        token.sessionId(),
                        user.getEmail(),
                        user.getDisplayName(),
                        user.getRole(),
                        user.getStatus(),
                        user.isEmailVerified(),
                        user.getCreatedAt(),
                        user.getLastLoginAt()
                )
        );
        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(
            HttpServletRequest request,
            HttpServletResponse response,
            String code,
            String detail
    ) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        problemWriter.write(
                request,
                response,
                HttpStatus.UNAUTHORIZED,
                code,
                "Authentication required",
                detail
        );
    }
}
