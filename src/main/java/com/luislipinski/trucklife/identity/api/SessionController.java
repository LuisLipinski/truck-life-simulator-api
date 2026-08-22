package com.luislipinski.trucklife.identity.api;

import com.luislipinski.trucklife.identity.application.IdentitySessionOperations;
import com.luislipinski.trucklife.identity.application.IssuedSession;
import com.luislipinski.trucklife.identity.application.RefreshTokenException;
import com.luislipinski.trucklife.identity.config.CsrfTokenService;
import com.luislipinski.trucklife.identity.config.IdentitySessionProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.WebUtils;

@RestController
@RequestMapping(path = "/api/v1/auth")
@Tag(name = "Identity", description = "Account registration, verification and sessions")
public class SessionController {

    private final IdentitySessionOperations sessionService;
    private final IdentitySessionProperties properties;
    private final SessionCookieFactory cookieFactory;
    private final CsrfTokenService csrfTokenService;

    public SessionController(
            IdentitySessionOperations sessionService,
            IdentitySessionProperties properties,
            SessionCookieFactory cookieFactory,
            CsrfTokenService csrfTokenService
    ) {
        this.sessionService = sessionService;
        this.properties = properties;
        this.cookieFactory = cookieFactory;
        this.csrfTokenService = csrfTokenService;
    }

    @GetMapping(path = "/csrf", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create or retrieve the CSRF token used by cookie-backed requests")
    @ApiResponse(responseCode = "200", description = "CSRF token available")
    public CsrfTokenResponse csrf(
            @Parameter(hidden = true) HttpServletResponse servletResponse
    ) {
        preventCaching(servletResponse);
        return new CsrfTokenResponse(
                csrfTokenService.issue(servletResponse),
                CsrfTokenService.HEADER_NAME
        );
    }

    @PostMapping(
            path = "/login",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(summary = "Authenticate and create a refreshable session")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Session created",
                    content = @Content(schema = @Schema(implementation = AccessTokenResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Invalid credentials",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(responseCode = "403", description = "Account cannot start a session"),
            @ApiResponse(responseCode = "429", description = "Login rate limit exceeded")
    })
    public AccessTokenResponse login(
            @RequestBody LoginRequest request,
            @Parameter(hidden = true) HttpServletRequest servletRequest,
            @Parameter(hidden = true) HttpServletResponse servletResponse
    ) {
        LoginRequest credentials = request == null
                ? new LoginRequest(null, null)
                : request;
        IssuedSession session = sessionService.login(
                credentials.email(),
                credentials.password(),
                servletRequest.getRemoteAddr(),
                servletRequest.getHeader(HttpHeaders.USER_AGENT)
        );
        preventCaching(servletResponse);
        setRefreshCookie(servletResponse, session);
        return accessTokenResponse(session);
    }

    @PostMapping(path = "/refresh", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Rotate the refresh token and issue another access token")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Session rotated",
                    content = @Content(schema = @Schema(implementation = AccessTokenResponse.class))
            ),
            @ApiResponse(responseCode = "401", description = "Refresh token invalid or expired"),
            @ApiResponse(responseCode = "403", description = "CSRF token or origin invalid"),
            @ApiResponse(responseCode = "429", description = "Refresh rate limit exceeded")
    })
    public AccessTokenResponse refresh(
            @Parameter(hidden = true) HttpServletRequest servletRequest,
            @Parameter(hidden = true) HttpServletResponse servletResponse
    ) {
        try {
            IssuedSession session = sessionService.refresh(
                    readRefreshToken(servletRequest),
                    servletRequest.getRemoteAddr(),
                    servletRequest.getHeader(HttpHeaders.USER_AGENT)
            );
            preventCaching(servletResponse);
            setRefreshCookie(servletResponse, session);
            return accessTokenResponse(session);
        } catch (RefreshTokenException exception) {
            clearRefreshCookie(servletResponse);
            throw exception;
        }
    }

    @PostMapping(path = "/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke the current session and clear its cookies")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Logout completed"),
            @ApiResponse(responseCode = "403", description = "CSRF token or origin invalid")
    })
    public void logout(
            @Parameter(hidden = true) HttpServletRequest servletRequest,
            @Parameter(hidden = true) HttpServletResponse servletResponse
    ) {
        sessionService.logout(readRefreshToken(servletRequest));
        clearRefreshCookie(servletResponse);
        csrfTokenService.clear(servletResponse);
    }

    private AccessTokenResponse accessTokenResponse(IssuedSession session) {
        return new AccessTokenResponse(
                session.accessToken(),
                "Bearer",
                properties.accessTokenTtl().toSeconds()
        );
    }

    private String readRefreshToken(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, cookieFactory.cookieName());
        return cookie == null ? null : cookie.getValue();
    }

    private void setRefreshCookie(HttpServletResponse response, IssuedSession session) {
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookieFactory.refreshCookie(session.rawRefreshToken()).toString()
        );
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookieFactory.clearedRefreshCookie().toString()
        );
    }

    private void preventCaching(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
    }
}
