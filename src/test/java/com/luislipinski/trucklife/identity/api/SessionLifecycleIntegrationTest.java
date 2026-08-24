package com.luislipinski.trucklife.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.luislipinski.trucklife.identity.application.JwtAccessTokenIssuer;
import com.luislipinski.trucklife.identity.application.RefreshTokenGenerator;
import com.luislipinski.trucklife.identity.application.TokenDigests;
import com.luislipinski.trucklife.identity.config.CsrfTokenService;
import com.luislipinski.trucklife.identity.domain.UserRole;
import com.luislipinski.trucklife.identity.domain.UserStatus;
import com.luislipinski.trucklife.identity.persistence.RefreshTokenEntity;
import com.luislipinski.trucklife.identity.persistence.RefreshTokenRepository;
import com.luislipinski.trucklife.identity.persistence.UserActionTokenRepository;
import com.luislipinski.trucklife.identity.persistence.UserEntity;
import com.luislipinski.trucklife.identity.persistence.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "identity.rate-limit.login.max-attempts=100",
                "identity.rate-limit.refresh.max-attempts=100"
        }
)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class SessionLifecycleIntegrationTest {

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String REFRESH_PATH = "/api/v1/auth/refresh";
    private static final String LOGOUT_PATH = "/api/v1/auth/logout";
    private static final String CSRF_PATH = "/api/v1/auth/csrf";
    private static final String REFRESH_COOKIE = "TLS_REFRESH_TOKEN";
    private static final String CSRF_COOKIE = "TLS_CSRF_TOKEN";
    private static final String ALLOWED_ORIGIN =
            "https://app.test.truck-life-simulator.local";
    private static final String PASSWORD = "a valid session password";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:18-alpine")
    );

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserActionTokenRepository actionTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtAccessTokenIssuer accessTokenIssuer;

    @Autowired
    private RefreshTokenGenerator refreshTokenGenerator;

    @BeforeEach
    void cleanPersistence() {
        refreshTokenRepository.deleteAllInBatch();
        actionTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void logsInAnActiveUserAndReturnsOnlyAnAccessTokenInJson() {
        UserEntity user = saveUser("driver@example.com", UserStatus.ACTIVE, true);

        EntityExchangeResult<AccessTokenResponse> result = login(
                " DRIVER@example.com ",
                PASSWORD,
                200
        );

        AccessTokenResponse body = result.getResponseBody();
        ResponseCookie refreshCookie = result.getResponseCookies().getFirst(REFRESH_COOKIE);
        assertThat(body).isNotNull();
        assertThat(body.tokenType()).isEqualTo("Bearer");
        assertThat(body.expiresIn()).isEqualTo(600);
        assertThat(body.accessToken()).hasSizeGreaterThan(100);
        assertThat(result.getResponseHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(result.getResponseHeaders().getPragma()).isEqualTo("no-cache");
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.isHttpOnly()).isTrue();
        assertThat(refreshCookie.isSecure()).isTrue();
        assertThat(refreshCookie.getSameSite()).isEqualTo("None");
        assertThat(refreshCookie.getPath()).isEqualTo("/api/v1/auth");
        assertThat(refreshCookie.getDomain()).isNull();
        assertThat(refreshCookie.getMaxAge()).hasSeconds(30L * 24 * 60 * 60);

        RefreshTokenEntity persisted = refreshTokenRepository.findByTokenHash(
                TokenDigests.sha256(refreshCookie.getValue())
        ).orElseThrow();
        UserEntity persistedUser = userRepository.findById(user.getId()).orElseThrow();
        JwtAccessTokenIssuer.DecodedAccessToken access = accessTokenIssuer.decodeAndValidate(
                body.accessToken()
        );
        assertThat(persisted.getTokenHash())
                .matches("[0-9a-f]{64}")
                .isNotEqualTo(refreshCookie.getValue());
        assertThat(persisted.getParent()).isNull();
        assertThat(persistedUser.getLastLoginAt()).isNotNull();
        assertThat(access.userId()).isEqualTo(user.getId());
        assertThat(access.sessionId()).isEqualTo(persisted.getFamilyId());
        assertThat(access.role()).isEqualTo(UserRole.USER);
        assertThat(access.emailVerified()).isTrue();
    }

    @Test
    void usesTheSameNeutralResponseForUnknownEmailAndWrongPassword() {
        saveUser("known@example.com", UserStatus.ACTIVE, true);

        assertInvalidCredentials("known@example.com", "wrong password value");
        assertInvalidCredentials("unknown@example.com", PASSWORD);

        assertThat(refreshTokenRepository.count()).isZero();
    }

    @Test
    void rejectsPendingLockedAndDisabledAccountsOnlyAfterThePasswordMatches() {
        saveUser("pending@example.com", UserStatus.PENDING_VERIFICATION, false);
        saveUser("locked@example.com", UserStatus.LOCKED, true);
        saveUser("disabled@example.com", UserStatus.DISABLED, true);

        assertLoginProblem("pending@example.com", PASSWORD, 403, "EMAIL_NOT_VERIFIED");
        assertLoginProblem("locked@example.com", PASSWORD, 403, "ACCOUNT_LOCKED");
        assertLoginProblem("disabled@example.com", PASSWORD, 403, "ACCOUNT_DISABLED");
        assertLoginProblem("locked@example.com", "wrong password value", 401, "INVALID_CREDENTIALS");

        assertThat(refreshTokenRepository.count()).isZero();
    }

    @Test
    void rotatesRefreshTokensAndRevokesTheFamilyWhenAnOldTokenIsReused() {
        saveUser("rotation@example.com", UserStatus.ACTIVE, true);
        EntityExchangeResult<AccessTokenResponse> login = login(
                "rotation@example.com",
                PASSWORD,
                200
        );
        String originalRawToken = refreshCookie(login).getValue();
        CsrfContext csrf = csrf();

        EntityExchangeResult<AccessTokenResponse> rotated = refresh(
                originalRawToken,
                csrf,
                200,
                null
        );
        String replacementRawToken = refreshCookie(rotated).getValue();
        assertThat(replacementRawToken).isNotEqualTo(originalRawToken);

        RefreshTokenEntity original = refreshTokenRepository.findByTokenHash(
                TokenDigests.sha256(originalRawToken)
        ).orElseThrow();
        RefreshTokenEntity replacement = refreshTokenRepository.findByTokenHash(
                TokenDigests.sha256(replacementRawToken)
        ).orElseThrow();
        assertThat(original.getRevokedAt()).isNotNull();
        assertThat(original.getReplacedBy().getId()).isEqualTo(replacement.getId());
        assertThat(replacement.getParent().getId()).isEqualTo(original.getId());
        assertThat(replacement.getFamilyId()).isEqualTo(original.getFamilyId());
        assertThat(accessTokenIssuer.decodeAndValidate(
                rotated.getResponseBody().accessToken()
        ).sessionId()).isEqualTo(original.getFamilyId());

        refresh(originalRawToken, csrf, 401, "REFRESH_TOKEN_REUSED");

        RefreshTokenEntity reused = refreshTokenRepository.findById(original.getId()).orElseThrow();
        RefreshTokenEntity revokedReplacement = refreshTokenRepository
                .findById(replacement.getId())
                .orElseThrow();
        assertThat(reused.getReuseDetectedAt()).isNotNull();
        assertThat(revokedReplacement.getRevokedAt()).isNotNull();

        refresh(replacementRawToken, csrf, 401, "REFRESH_TOKEN_INVALID");
    }

    @Test
    void rejectsAndRevokesAnExpiredRefreshToken() {
        UserEntity user = saveUser("expired-session@example.com", UserStatus.ACTIVE, true);
        RefreshTokenGenerator.GeneratedRefreshToken generated = refreshTokenGenerator.generate();
        Instant now = Instant.now();
        RefreshTokenEntity expired = refreshTokenRepository.saveAndFlush(new RefreshTokenEntity(
                UUID.randomUUID(),
                user,
                UUID.randomUUID(),
                null,
                generated.tokenHash(),
                now.minus(31, ChronoUnit.DAYS),
                now.minus(1, ChronoUnit.DAYS),
                null,
                null,
                "203.0.113.10",
                "integration-test"
        ));

        refresh(generated.rawToken(), csrf(), 401, "REFRESH_TOKEN_EXPIRED");

        assertThat(refreshTokenRepository.findById(expired.getId()).orElseThrow().getRevokedAt())
                .isNotNull();
    }

    @Test
    void logsOutIdempotentlyRevokesTheFamilyAndClearsBothCookies() {
        saveUser("logout@example.com", UserStatus.ACTIVE, true);
        String rawRefreshToken = refreshCookie(login(
                "logout@example.com",
                PASSWORD,
                200
        )).getValue();
        CsrfContext csrf = csrf();

        EntityExchangeResult<Void> first = logout(rawRefreshToken, csrf);
        EntityExchangeResult<Void> repeated = logout(rawRefreshToken, csrf);

        assertThat(first.getResponseCookies().getFirst(REFRESH_COOKIE).getMaxAge()).isZero();
        assertThat(first.getResponseCookies().getFirst(CSRF_COOKIE).getMaxAge()).isZero();
        assertThat(repeated.getStatus().value()).isEqualTo(204);
        RefreshTokenEntity token = refreshTokenRepository.findByTokenHash(
                TokenDigests.sha256(rawRefreshToken)
        ).orElseThrow();
        assertThat(token.getRevokedAt()).isNotNull();
        refresh(rawRefreshToken, csrf, 401, "REFRESH_TOKEN_INVALID");
    }

    @Test
    void requiresAllowedOriginAndCsrfAndConfiguresCredentialedCors() {
        saveUser("browser-security@example.com", UserStatus.ACTIVE, true);
        String rawRefreshToken = refreshCookie(login(
                "browser-security@example.com",
                PASSWORD,
                200
        )).getValue();
        CsrfContext csrf = csrf();

        restTestClient.post()
                .uri(REFRESH_PATH)
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .cookie(REFRESH_COOKIE, rawRefreshToken)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("CSRF_TOKEN_INVALID");

        restTestClient.post()
                .uri(REFRESH_PATH)
                .header(HttpHeaders.ORIGIN, "https://attacker.example")
                .header(CsrfTokenService.HEADER_NAME, csrf.token())
                .cookie(REFRESH_COOKIE, rawRefreshToken)
                .cookie(CSRF_COOKIE, csrf.cookieValue())
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("ORIGIN_NOT_ALLOWED");

        restTestClient.options()
                .uri(REFRESH_PATH)
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, CsrfTokenService.HEADER_NAME)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN)
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");

        restTestClient.options()
                .uri(REFRESH_PATH)
                .header(HttpHeaders.ORIGIN, "https://attacker.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void documentsTheSessionOperationsInOpenApi() {
        restTestClient.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.paths['/api/v1/auth/csrf'].get.responses['200']").exists()
                .jsonPath("$.paths['/api/v1/auth/login'].post.responses['200']").exists()
                .jsonPath("$.paths['/api/v1/auth/refresh'].post.responses['200']").exists()
                .jsonPath("$.paths['/api/v1/auth/logout'].post.responses['204']").exists();
    }

    @Test
    void neverWritesPasswordsOrTokensToCapturedLogs(CapturedOutput output) {
        String rawPassword = "a unique secret password";
        saveUser("redaction@example.com", UserStatus.ACTIVE, true, rawPassword);
        EntityExchangeResult<AccessTokenResponse> login = login(
                "redaction@example.com",
                rawPassword,
                200
        );
        String rawRefreshToken = refreshCookie(login).getValue();
        CsrfContext csrf = csrf();
        EntityExchangeResult<AccessTokenResponse> rotated = refresh(
                rawRefreshToken,
                csrf,
                200,
                null
        );

        assertThat(output.getAll()).doesNotContain(
                rawPassword,
                rawRefreshToken,
                refreshCookie(rotated).getValue(),
                csrf.token(),
                login.getResponseBody().accessToken(),
                rotated.getResponseBody().accessToken()
        );
    }

    private EntityExchangeResult<AccessTokenResponse> login(
            String email,
            String password,
            int expectedStatus
    ) {
        RestTestClient.ResponseSpec response = restTestClient.post()
                .uri(LOGIN_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", password))
                .exchange()
                .expectStatus().isEqualTo(expectedStatus);
        return response.expectBody(AccessTokenResponse.class).returnResult();
    }

    private void assertInvalidCredentials(String email, String password) {
        assertLoginProblem(email, password, 401, "INVALID_CREDENTIALS");
    }

    private void assertLoginProblem(
            String email,
            String password,
            int status,
            String code
    ) {
        restTestClient.post()
                .uri(LOGIN_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", password))
                .exchange()
                .expectStatus().isEqualTo(status)
                .expectBody()
                .jsonPath("$.code").isEqualTo(code);
    }

    private CsrfContext csrf() {
        EntityExchangeResult<CsrfTokenResponse> result = restTestClient.get()
                .uri(CSRF_PATH)
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .exchange()
                .expectStatus().isOk()
                .expectBody(CsrfTokenResponse.class)
                .returnResult();
        return new CsrfContext(
                result.getResponseBody().token(),
                result.getResponseCookies().getFirst(CSRF_COOKIE).getValue()
        );
    }

    private EntityExchangeResult<AccessTokenResponse> refresh(
            String rawRefreshToken,
            CsrfContext csrf,
            int expectedStatus,
            String expectedCode
    ) {
        RestTestClient.ResponseSpec response = restTestClient.post()
                .uri(REFRESH_PATH)
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(CsrfTokenService.HEADER_NAME, csrf.token())
                .cookie(REFRESH_COOKIE, rawRefreshToken)
                .cookie(CSRF_COOKIE, csrf.cookieValue())
                .exchange()
                .expectStatus().isEqualTo(expectedStatus);
        if (expectedCode == null) {
            return response.expectBody(AccessTokenResponse.class).returnResult();
        }
        response.expectBody()
                .jsonPath("$.code").isEqualTo(expectedCode)
                .returnResult();
        return null;
    }

    private EntityExchangeResult<Void> logout(String rawRefreshToken, CsrfContext csrf) {
        return restTestClient.post()
                .uri(LOGOUT_PATH)
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(CsrfTokenService.HEADER_NAME, csrf.token())
                .cookie(REFRESH_COOKIE, rawRefreshToken)
                .cookie(CSRF_COOKIE, csrf.cookieValue())
                .exchange()
                .expectStatus().isNoContent()
                .returnResult(Void.class);
    }

    private ResponseCookie refreshCookie(EntityExchangeResult<?> result) {
        return result.getResponseCookies().getFirst(REFRESH_COOKIE);
    }

    private UserEntity saveUser(String email, UserStatus status, boolean verified) {
        return saveUser(email, status, verified, PASSWORD);
    }

    private UserEntity saveUser(
            String email,
            UserStatus status,
            boolean verified,
            String password
    ) {
        Instant createdAt = Instant.now().minus(1, ChronoUnit.MINUTES);
        return userRepository.saveAndFlush(new UserEntity(
                UUID.randomUUID(),
                email,
                email.toLowerCase(),
                passwordEncoder.encode(password),
                "Session Driver",
                status,
                UserRole.USER,
                verified,
                verified ? createdAt : null,
                createdAt,
                createdAt,
                null
        ));
    }

    private record CsrfContext(String token, String cookieValue) {
    }
}
