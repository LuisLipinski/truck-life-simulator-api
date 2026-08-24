package com.luislipinski.trucklife.identity.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.luislipinski.trucklife.identity.application.AuthenticatedAccount;
import com.luislipinski.trucklife.identity.application.JwtAccessTokenIssuer;
import com.luislipinski.trucklife.identity.domain.UserRole;
import com.luislipinski.trucklife.identity.domain.UserStatus;
import com.luislipinski.trucklife.identity.persistence.UserEntity;
import com.luislipinski.trucklife.identity.persistence.UserRepository;
import com.luislipinski.trucklife.shared.error.ApiSecurityProblemWriter;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AccessTokenAuthenticationFilterTest {

    private JwtAccessTokenIssuer accessTokenIssuer;
    private UserRepository userRepository;
    private ApiSecurityProblemWriter problemWriter;
    private AccessTokenAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        accessTokenIssuer = mock(JwtAccessTokenIssuer.class);
        userRepository = mock(UserRepository.class);
        problemWriter = mock(ApiSecurityProblemWriter.class);
        filter = new AccessTokenAuthenticationFilter(
                accessTokenIssuer,
                userRepository,
                problemWriter
        );
    }

    @Test
    void skipsRequestsOutsideTheProtectedAccountEndpoint() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/platform");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(accessTokenIssuer, userRepository, problemWriter);
    }

    @Test
    void requiresABearerToken() throws Exception {
        MockHttpServletRequest request = meRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
        verify(problemWriter).write(
                request,
                response,
                HttpStatus.UNAUTHORIZED,
                "AUTHENTICATION_REQUIRED",
                "Authentication required",
                "A Bearer access token is required"
        );
        verify(chain, never()).doFilter(any(), any());
        verifyNoInteractions(accessTokenIssuer, userRepository);
    }

    @Test
    void rejectsATokenWhenThePersistedRoleNoLongerMatches() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        MockHttpServletRequest request = meRequestWithBearer("access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        when(accessTokenIssuer.decodeAndValidate("access-token"))
                .thenReturn(decoded(userId, sessionId, UserRole.USER, true));
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user(userId, UserRole.ADMIN, UserStatus.ACTIVE, true)));

        filter.doFilter(request, response, chain);

        verify(problemWriter).write(
                request,
                response,
                HttpStatus.UNAUTHORIZED,
                "ACCESS_TOKEN_INVALID",
                "Authentication required",
                "The access token is no longer valid"
        );
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void forbidsAnInactivePersistedAccount() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        MockHttpServletRequest request = meRequestWithBearer("access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        when(accessTokenIssuer.decodeAndValidate("access-token"))
                .thenReturn(decoded(userId, sessionId, UserRole.USER, true));
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user(userId, UserRole.USER, UserStatus.DISABLED, true)));

        filter.doFilter(request, response, chain);

        verify(problemWriter).write(
                request,
                response,
                HttpStatus.FORBIDDEN,
                "ACCOUNT_FORBIDDEN",
                "Account access forbidden",
                "The account cannot access protected resources"
        );
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void authenticatesTheCurrentActiveAccountAndContinuesTheChain() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        MockHttpServletRequest request = meRequestWithBearer("access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        UserEntity persistedUser = user(userId, UserRole.USER, UserStatus.ACTIVE, true);
        when(accessTokenIssuer.decodeAndValidate("access-token"))
                .thenReturn(decoded(userId, sessionId, UserRole.USER, true));
        when(userRepository.findById(userId)).thenReturn(Optional.of(persistedUser));

        filter.doFilter(request, response, chain);

        Object attribute = request.getAttribute(
                AccessTokenAuthenticationFilter.AUTHENTICATED_ACCOUNT_ATTRIBUTE
        );
        assertThat(attribute).isInstanceOf(AuthenticatedAccount.class);
        AuthenticatedAccount account = (AuthenticatedAccount) attribute;
        assertThat(account.userId()).isEqualTo(userId);
        assertThat(account.sessionId()).isEqualTo(sessionId);
        assertThat(account.email()).isEqualTo("driver@example.com");
        assertThat(account.role()).isEqualTo(UserRole.USER);
        assertThat(account.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(account.emailVerified()).isTrue();
        verify(chain).doFilter(request, response);
        verify(problemWriter, never()).write(
                eq(request), eq(response), any(), any(), any(), any()
        );
    }

    private MockHttpServletRequest meRequest() {
        return new MockHttpServletRequest("GET", "/api/v1/me");
    }

    private MockHttpServletRequest meRequestWithBearer(String token) {
        MockHttpServletRequest request = meRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return request;
    }

    private JwtAccessTokenIssuer.DecodedAccessToken decoded(
            UUID userId,
            UUID sessionId,
            UserRole role,
            boolean emailVerified
    ) {
        Instant now = Instant.parse("2026-08-24T12:00:00Z");
        return new JwtAccessTokenIssuer.DecodedAccessToken(
                userId,
                sessionId,
                role,
                emailVerified,
                "truck-life-simulator-api",
                "truck-life-simulator-web",
                now,
                now.plusSeconds(600),
                UUID.randomUUID()
        );
    }

    private UserEntity user(
            UUID id,
            UserRole role,
            UserStatus status,
            boolean emailVerified
    ) {
        Instant now = Instant.parse("2026-08-24T12:00:00Z");
        return new UserEntity(
                id,
                "driver@example.com",
                "driver@example.com",
                "encoded-password",
                "Road Driver",
                status,
                role,
                emailVerified,
                emailVerified ? now.minusSeconds(3600) : null,
                now.minusSeconds(7200),
                now.minusSeconds(3600),
                now.minusSeconds(300)
        );
    }
}
