package com.luislipinski.trucklife.identity.api;

import com.luislipinski.trucklife.identity.application.AccountAuthorization;
import com.luislipinski.trucklife.identity.application.AuthenticatedAccount;
import com.luislipinski.trucklife.identity.application.IdentityAccountOperations;
import com.luislipinski.trucklife.identity.config.AccessTokenAuthenticationFilter;
import com.luislipinski.trucklife.identity.domain.UserRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/me")
@Tag(name = "My Account", description = "Authenticated account information and security operations")
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
@SecurityRequirement(name = "bearerAuth")
public class MeController {

    private final AccountAuthorization authorization;
    private final IdentityAccountOperations accountOperations;

    public MeController(AccountAuthorization authorization, IdentityAccountOperations accountOperations) {
        this.authorization = authorization;
        this.accountOperations = accountOperations;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Return the currently authenticated account")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Authenticated account",
                    content = @Content(schema = @Schema(implementation = MyAccountResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Access token missing, invalid or expired",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Authenticated account cannot access the resource",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    public ResponseEntity<MyAccountResponse> me(HttpServletRequest request) {
        AuthenticatedAccount account = authenticatedAccount(request);
        authorization.requireAnyRole(account, UserRole.USER, UserRole.ADMIN);

        MyAccountResponse response = new MyAccountResponse(
                account.userId(),
                account.email(),
                account.displayName(),
                account.role(),
                account.status(),
                account.emailVerified(),
                account.createdAt(),
                account.lastLoginAt()
        );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @PostMapping(path = "/change-password", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Change the password of the currently authenticated account")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Password changed; refresh sessions revoked"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Current password invalid or new password violates the password policy",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Access token missing, invalid or expired",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Authenticated account cannot access the resource",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedAccount account = authenticatedAccount(servletRequest);
        authorization.requireAnyRole(account, UserRole.USER, UserRole.ADMIN);
        accountOperations.changePassword(account.userId(), request.currentPassword(), request.newPassword());

        return ResponseEntity.noContent()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }

    private AuthenticatedAccount authenticatedAccount(HttpServletRequest request) {
        return (AuthenticatedAccount) request.getAttribute(
                AccessTokenAuthenticationFilter.AUTHENTICATED_ACCOUNT_ATTRIBUTE
        );
    }
}
